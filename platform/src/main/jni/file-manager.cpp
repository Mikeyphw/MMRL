#ifndef MMRL_HOST_NATIVE_CONTRACT
#include <jni.h>
#endif
#include <cerrno>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <limits>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>
#include "include/file_manager_safe.hpp"

namespace mmrl::safe_file {

std::vector<std::string> split_relative(const char* raw) {
    std::vector<std::string> parts;
    if (raw == nullptr || *raw == '\0') return parts;
    std::string current;
    for (const char* p = raw;; ++p) {
        const char ch = *p;
        if (ch == '/' || ch == '\0') {
            if (current.empty() || current == "." || current == "..") return {};
            parts.push_back(current);
            current.clear();
            if (ch == '\0') break;
        } else {
            current.push_back(ch);
        }
    }
    return parts;
}

int open_root(const char* root) {
    if (root == nullptr || root[0] != '/') {
        errno = EINVAL;
        return -1;
    }
    return open(root, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
}

int walk_directory(int root_fd, const std::vector<std::string>& parts, size_t count) {
    int dir_fd = dup(root_fd);
    if (dir_fd < 0) return -1;
    for (size_t i = 0; i < count; ++i) {
        const int next_fd = openat(
                dir_fd,
                parts[i].c_str(),
                O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
        const int saved_errno = errno;
        close(dir_fd);
        if (next_fd < 0) {
            errno = saved_errno;
            return -1;
        }
        dir_fd = next_fd;
    }
    return dir_fd;
}

int safe_open_at(const char* root, const char* relative, int flags, mode_t mode) {
    if (relative == nullptr || relative[0] == '/') {
        errno = EINVAL;
        return -1;
    }

    const auto parts = split_relative(relative);
    if (*relative != '\0' && parts.empty()) {
        errno = EINVAL;
        return -1;
    }

    if (parts.empty()) {
        return open(root, flags | O_CLOEXEC | O_NOFOLLOW, mode);
    }

    const int root_fd = open_root(root);
    if (root_fd < 0) return -1;
    const int dir_fd = walk_directory(root_fd, parts, parts.size() - 1);
    const int walk_errno = errno;
    close(root_fd);
    if (dir_fd < 0) {
        errno = walk_errno;
        return -1;
    }

    const int fd = openat(dir_fd, parts.back().c_str(), flags | O_CLOEXEC | O_NOFOLLOW, mode);
    const int saved_errno = errno;
    close(dir_fd);
    errno = saved_errno;
    return fd;
}

int safe_metadata_fd(const char* root, const char* relative) {
    return safe_open_at(root, relative, O_RDONLY | O_NONBLOCK, 0);
}

bool delete_entry_no_follow(int parent_fd, const char* name) {
    struct stat st{};
    if (fstatat(parent_fd, name, &st, AT_SYMLINK_NOFOLLOW) != 0) return false;

    if (S_ISDIR(st.st_mode)) {
        const int child_fd = openat(
                parent_fd,
                name,
                O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
        if (child_fd < 0) return false;

        DIR* dir = fdopendir(dup(child_fd));
        if (dir == nullptr) {
            close(child_fd);
            return false;
        }

        bool ok = true;
        errno = 0;
        while (dirent* entry = readdir(dir)) {
            if (std::strcmp(entry->d_name, ".") == 0 || std::strcmp(entry->d_name, "..") == 0) continue;
            if (!delete_entry_no_follow(child_fd, entry->d_name)) {
                ok = false;
                break;
            }
            errno = 0;
        }
        if (errno != 0) ok = false;
        closedir(dir);
        close(child_fd);
        if (!ok) return false;
        return unlinkat(parent_fd, name, AT_REMOVEDIR) == 0;
    }

    return unlinkat(parent_fd, name, 0) == 0;
}

bool safe_delete_at(const char* root, const char* relative) {
    if (relative == nullptr || relative[0] == '/' || relative[0] == '\0') {
        errno = EINVAL;  // Never delete an approved root itself.
        return false;
    }
    const auto parts = split_relative(relative);
    if (parts.empty()) {
        errno = EINVAL;
        return false;
    }
    const int root_fd = open_root(root);
    if (root_fd < 0) return false;
    const int parent_fd = walk_directory(root_fd, parts, parts.size() - 1);
    const int walk_errno = errno;
    close(root_fd);
    if (parent_fd < 0) {
        errno = walk_errno;
        return false;
    }
    const bool result = delete_entry_no_follow(parent_fd, parts.back().c_str());
    const int saved_errno = errno;
    close(parent_fd);
    errno = saved_errno;
    return result;
}


bool stat_at_no_follow(const char* root, const char* relative, struct stat* out) {
    if (out == nullptr || relative == nullptr || relative[0] == '/') {
        errno = EINVAL;
        return false;
    }
    const auto parts = split_relative(relative);
    if (*relative != '\0' && parts.empty()) {
        errno = EINVAL;
        return false;
    }
    const int root_fd = open_root(root);
    if (root_fd < 0) return false;
    if (parts.empty()) {
        const bool ok = fstat(root_fd, out) == 0;
        const int saved_errno = errno;
        close(root_fd);
        errno = saved_errno;
        return ok;
    }
    const int parent_fd = walk_directory(root_fd, parts, parts.size() - 1);
    const int walk_errno = errno;
    close(root_fd);
    if (parent_fd < 0) {
        errno = walk_errno;
        return false;
    }
    const bool ok = fstatat(parent_fd, parts.back().c_str(), out, AT_SYMLINK_NOFOLLOW) == 0;
    const int saved_errno = errno;
    close(parent_fd);
    errno = saved_errno;
    return ok;
}

bool safe_access_at(const char* root, const char* relative, int mode) {
    if (relative == nullptr || relative[0] == '/') {
        errno = EINVAL;
        return false;
    }
    const auto parts = split_relative(relative);
    if (*relative != '\0' && parts.empty()) {
        errno = EINVAL;
        return false;
    }
    const int root_fd = open_root(root);
    if (root_fd < 0) return false;
    if (parts.empty()) {
        const bool ok = faccessat(root_fd, ".", mode, AT_EACCESS | AT_SYMLINK_NOFOLLOW) == 0;
        const int saved_errno = errno;
        close(root_fd);
        errno = saved_errno;
        return ok;
    }
    const int parent_fd = walk_directory(root_fd, parts, parts.size() - 1);
    const int walk_errno = errno;
    close(root_fd);
    if (parent_fd < 0) {
        errno = walk_errno;
        return false;
    }
    struct stat st{};
    const bool not_symlink =
            fstatat(parent_fd, parts.back().c_str(), &st, AT_SYMLINK_NOFOLLOW) == 0 && !S_ISLNK(st.st_mode);
    const bool ok = not_symlink &&
            faccessat(parent_fd, parts.back().c_str(), mode, AT_EACCESS | AT_SYMLINK_NOFOLLOW) == 0;
    const int saved_errno = errno;
    close(parent_fd);
    errno = saved_errno;
    return ok;
}

bool safe_mkdir_at(const char* root, const char* relative, bool recursive) {
    if (relative == nullptr || relative[0] == '/' || relative[0] == '\0') {
        errno = EINVAL;
        return false;
    }
    const auto parts = split_relative(relative);
    if (parts.empty()) {
        errno = EINVAL;
        return false;
    }
    const int root_fd = open_root(root);
    if (root_fd < 0) return false;

    if (!recursive) {
        const int parent_fd = walk_directory(root_fd, parts, parts.size() - 1);
        const int walk_errno = errno;
        close(root_fd);
        if (parent_fd < 0) {
            errno = walk_errno;
            return false;
        }
        const bool ok = mkdirat(parent_fd, parts.back().c_str(), 0777) == 0;
        const int saved_errno = errno;
        close(parent_fd);
        errno = saved_errno;
        return ok;
    }

    int dir_fd = dup(root_fd);
    close(root_fd);
    if (dir_fd < 0) return false;
    bool created_any = false;
    for (const auto& part : parts) {
        if (mkdirat(dir_fd, part.c_str(), 0777) == 0) {
            created_any = true;
        } else if (errno != EEXIST) {
            const int saved_errno = errno;
            close(dir_fd);
            errno = saved_errno;
            return false;
        }
        const int next_fd = openat(dir_fd, part.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
        const int saved_errno = errno;
        close(dir_fd);
        if (next_fd < 0) {
            errno = saved_errno;
            return false;
        }
        dir_fd = next_fd;
    }
    close(dir_fd);
    return created_any;
}

bool safe_create_at(const char* root, const char* relative) {
    if (relative == nullptr || relative[0] == '\0') {
        errno = EINVAL;
        return false;
    }
    const int fd = safe_open_at(root, relative, O_WRONLY | O_CREAT | O_EXCL, 0666);
    if (fd < 0) return false;
    return close(fd) == 0;
}

int open_parent_for(const char* root, const char* relative, std::string* leaf) {
    if (leaf == nullptr || relative == nullptr || relative[0] == '/' || relative[0] == '\0') {
        errno = EINVAL;
        return -1;
    }
    const auto parts = split_relative(relative);
    if (parts.empty()) {
        errno = EINVAL;
        return -1;
    }
    const int root_fd = open_root(root);
    if (root_fd < 0) return -1;
    const int parent_fd = walk_directory(root_fd, parts, parts.size() - 1);
    const int walk_errno = errno;
    close(root_fd);
    if (parent_fd < 0) {
        errno = walk_errno;
        return -1;
    }
    *leaf = parts.back();
    return parent_fd;
}

bool safe_rename_at(
        const char* source_root,
        const char* source_relative,
        const char* target_root,
        const char* target_relative) {
    std::string source_leaf;
    std::string target_leaf;
    const int source_parent = open_parent_for(source_root, source_relative, &source_leaf);
    if (source_parent < 0) return false;
    const int target_parent = open_parent_for(target_root, target_relative, &target_leaf);
    if (target_parent < 0) {
        const int saved_errno = errno;
        close(source_parent);
        errno = saved_errno;
        return false;
    }
    const bool ok = renameat(source_parent, source_leaf.c_str(), target_parent, target_leaf.c_str()) == 0;
    const int saved_errno = errno;
    close(target_parent);
    close(source_parent);
    errno = saved_errno;
    return ok;
}

bool write_all(int fd, const char* buffer, size_t size) {
    size_t offset = 0;
    while (offset < size) {
        const ssize_t written = write(fd, buffer + offset, size - offset);
        if (written < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (written == 0) {
            errno = EIO;
            return false;
        }
        offset += static_cast<size_t>(written);
    }
    return true;
}

bool safe_copy_at(
        const char* source_root,
        const char* source_relative,
        const char* target_root,
        const char* target_relative,
        bool overwrite) {
    const int source_fd = safe_open_at(source_root, source_relative, O_RDONLY, 0);
    if (source_fd < 0) return false;
    struct stat source_stat{};
    if (fstat(source_fd, &source_stat) != 0 || !S_ISREG(source_stat.st_mode)) {
        const int saved_errno = errno == 0 ? EINVAL : errno;
        close(source_fd);
        errno = saved_errno;
        return false;
    }

    int target_fd = -1;
    bool created = false;
    if (overwrite) {
        target_fd = safe_open_at(target_root, target_relative, O_WRONLY, 0);
        if (target_fd < 0 && errno == ENOENT) {
            target_fd = safe_open_at(
                    target_root,
                    target_relative,
                    O_WRONLY | O_CREAT | O_EXCL,
                    static_cast<mode_t>(source_stat.st_mode & 0777));
            created = target_fd >= 0;
        }
    } else {
        target_fd = safe_open_at(
                target_root,
                target_relative,
                O_WRONLY | O_CREAT | O_EXCL,
                static_cast<mode_t>(source_stat.st_mode & 0777));
        created = target_fd >= 0;
    }
    if (target_fd < 0) {
        const int saved_errno = errno;
        close(source_fd);
        errno = saved_errno;
        return false;
    }

    struct stat target_stat{};
    bool ok = fstat(target_fd, &target_stat) == 0 &&
            !(source_stat.st_dev == target_stat.st_dev && source_stat.st_ino == target_stat.st_ino);
    if (ok && overwrite && !created) ok = ftruncate(target_fd, 0) == 0;

    char buffer[64 * 1024];
    while (ok) {
        const ssize_t count = read(source_fd, buffer, sizeof(buffer));
        if (count < 0) {
            if (errno == EINTR) continue;
            ok = false;
            break;
        }
        if (count == 0) break;
        ok = write_all(target_fd, buffer, static_cast<size_t>(count));
    }

    int saved_errno = errno;
    if (close(target_fd) != 0 && ok) {
        ok = false;
        saved_errno = errno;
    }
    if (close(source_fd) != 0 && ok) {
        ok = false;
        saved_errno = errno;
    }
    errno = saved_errno;
    return ok;
}

std::vector<std::string> safe_list_at(const char* root, const char* relative, bool* ok) {
    std::vector<std::string> result;
    if (ok != nullptr) *ok = false;
    const int fd = safe_open_at(root, relative, O_RDONLY | O_DIRECTORY, 0);
    if (fd < 0) return result;
    DIR* dir = fdopendir(fd);
    if (dir == nullptr) {
        close(fd);
        return result;
    }
    errno = 0;
    while (dirent* entry = readdir(dir)) {
        if (std::strcmp(entry->d_name, ".") == 0 || std::strcmp(entry->d_name, "..") == 0) continue;
        result.emplace_back(entry->d_name);
        errno = 0;
    }
    const bool success = errno == 0;
    closedir(dir);  // closes fd
    if (ok != nullptr) *ok = success;
    return result;
}

}  // namespace mmrl::safe_file

#ifndef MMRL_HOST_NATIVE_CONTRACT
using namespace mmrl::safe_file;

bool get_utf(JNIEnv* env, jstring value, const char** out) {
    if (value == nullptr || out == nullptr) return false;
    *out = env->GetStringUTFChars(value, nullptr);
    return *out != nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeSetOwnerAt(
        JNIEnv *env, jobject, jstring root, jstring relative, jint owner, jint group) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return JNI_FALSE;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return JNI_FALSE;
    }
    const int fd = safe_metadata_fd(root_raw, relative_raw);
    const bool result = fd >= 0 && fchown(fd, owner, group) == 0;
    const int saved_errno = errno;
    if (fd >= 0) close(fd);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    errno = saved_errno;
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeSetPermissionsAt(
        JNIEnv *env, jobject, jstring root, jstring relative, jint mode) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return JNI_FALSE;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return JNI_FALSE;
    }
    const int fd = safe_metadata_fd(root_raw, relative_raw);
    const bool result = fd >= 0 && fchmod(fd, static_cast<mode_t>(mode)) == 0;
    const int saved_errno = errno;
    if (fd >= 0) close(fd);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    errno = saved_errno;
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeDeleteAt(
        JNIEnv *env, jobject, jstring root, jstring relative) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return JNI_FALSE;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return JNI_FALSE;
    }
    const bool result = safe_delete_at(root_raw, relative_raw);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeOpenAt(
        JNIEnv *env, jobject, jstring root, jstring relative, jint flags, jint mode) {
    if (root == nullptr || relative == nullptr) return -EINVAL;
    const char* root_raw = env->GetStringUTFChars(root, nullptr);
    if (root_raw == nullptr) return -ENOMEM;
    const char* relative_raw = env->GetStringUTFChars(relative, nullptr);
    if (relative_raw == nullptr) {
        env->ReleaseStringUTFChars(root, root_raw);
        return -ENOMEM;
    }

    const int fd = safe_open_at(root_raw, relative_raw, flags, static_cast<mode_t>(mode));
    const int saved_errno = errno;
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    return fd >= 0 ? fd : -saved_errno;
}


extern "C" JNIEXPORT jint JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeModeAt(
        JNIEnv *env, jobject, jstring root, jstring relative) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return 0;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return 0;
    }
    struct stat st{};
    const bool ok = stat_at_no_follow(root_raw, relative_raw, &st);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    return ok ? static_cast<jint>(st.st_mode) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeSizeAt(
        JNIEnv *env, jobject, jstring root, jstring relative) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return -1;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return -1;
    }
    struct stat st{};
    const bool ok = stat_at_no_follow(root_raw, relative_raw, &st);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    return ok ? static_cast<jlong>(st.st_size) : -1;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeMtimeAt(
        JNIEnv *env, jobject, jstring root, jstring relative) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return -1;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return -1;
    }
    struct stat st{};
    const bool ok = stat_at_no_follow(root_raw, relative_raw, &st);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    if (!ok) return -1;
#if defined(__APPLE__)
    return static_cast<jlong>(st.st_mtimespec.tv_sec) * 1000L + st.st_mtimespec.tv_nsec / 1000000L;
#else
    return static_cast<jlong>(st.st_mtim.tv_sec) * 1000L + st.st_mtim.tv_nsec / 1000000L;
#endif
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeListAt(
        JNIEnv *env, jobject, jstring root, jstring relative) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return nullptr;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return nullptr;
    }
    bool ok = false;
    const auto names = safe_list_at(root_raw, relative_raw, &ok);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    if (!ok || names.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;

    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) return nullptr;
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(names.size()), string_class, nullptr);
    env->DeleteLocalRef(string_class);
    if (result == nullptr) return nullptr;
    for (jsize i = 0; i < static_cast<jsize>(names.size()); ++i) {
        jstring value = env->NewStringUTF(names[static_cast<size_t>(i)].c_str());
        if (value == nullptr) return nullptr;
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
        if (env->ExceptionCheck()) return nullptr;
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeAccessAt(
        JNIEnv *env, jobject, jstring root, jstring relative, jint mode) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return JNI_FALSE;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return JNI_FALSE;
    }
    const bool ok = safe_access_at(root_raw, relative_raw, mode);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeMkdirAt(
        JNIEnv *env, jobject, jstring root, jstring relative, jboolean recursive) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return JNI_FALSE;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return JNI_FALSE;
    }
    const bool ok = safe_mkdir_at(root_raw, relative_raw, recursive == JNI_TRUE);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeCreateAt(
        JNIEnv *env, jobject, jstring root, jstring relative) {
    const char* root_raw = nullptr;
    const char* relative_raw = nullptr;
    if (!get_utf(env, root, &root_raw)) return JNI_FALSE;
    if (!get_utf(env, relative, &relative_raw)) {
        env->ReleaseStringUTFChars(root, root_raw);
        return JNI_FALSE;
    }
    const bool ok = safe_create_at(root_raw, relative_raw);
    env->ReleaseStringUTFChars(relative, relative_raw);
    env->ReleaseStringUTFChars(root, root_raw);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeRenameAt(
        JNIEnv *env,
        jobject,
        jstring source_root,
        jstring source_relative,
        jstring target_root,
        jstring target_relative) {
    const char* source_root_raw = nullptr;
    const char* source_relative_raw = nullptr;
    const char* target_root_raw = nullptr;
    const char* target_relative_raw = nullptr;
    if (!get_utf(env, source_root, &source_root_raw)) return JNI_FALSE;
    if (!get_utf(env, source_relative, &source_relative_raw)) goto cleanup_source_root;
    if (!get_utf(env, target_root, &target_root_raw)) goto cleanup_source_relative;
    if (!get_utf(env, target_relative, &target_relative_raw)) goto cleanup_target_root;
    {
        const bool ok = safe_rename_at(
                source_root_raw, source_relative_raw, target_root_raw, target_relative_raw);
        env->ReleaseStringUTFChars(target_relative, target_relative_raw);
        env->ReleaseStringUTFChars(target_root, target_root_raw);
        env->ReleaseStringUTFChars(source_relative, source_relative_raw);
        env->ReleaseStringUTFChars(source_root, source_root_raw);
        return ok ? JNI_TRUE : JNI_FALSE;
    }
cleanup_target_root:
    env->ReleaseStringUTFChars(target_root, target_root_raw);
cleanup_source_relative:
    env->ReleaseStringUTFChars(source_relative, source_relative_raw);
cleanup_source_root:
    env->ReleaseStringUTFChars(source_root, source_root_raw);
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_file_FileManager_nativeCopyAt(
        JNIEnv *env,
        jobject,
        jstring source_root,
        jstring source_relative,
        jstring target_root,
        jstring target_relative,
        jboolean overwrite) {
    const char* source_root_raw = nullptr;
    const char* source_relative_raw = nullptr;
    const char* target_root_raw = nullptr;
    const char* target_relative_raw = nullptr;
    if (!get_utf(env, source_root, &source_root_raw)) return JNI_FALSE;
    if (!get_utf(env, source_relative, &source_relative_raw)) goto cleanup_copy_source_root;
    if (!get_utf(env, target_root, &target_root_raw)) goto cleanup_copy_source_relative;
    if (!get_utf(env, target_relative, &target_relative_raw)) goto cleanup_copy_target_root;
    {
        const bool ok = safe_copy_at(
                source_root_raw,
                source_relative_raw,
                target_root_raw,
                target_relative_raw,
                overwrite == JNI_TRUE);
        env->ReleaseStringUTFChars(target_relative, target_relative_raw);
        env->ReleaseStringUTFChars(target_root, target_root_raw);
        env->ReleaseStringUTFChars(source_relative, source_relative_raw);
        env->ReleaseStringUTFChars(source_root, source_root_raw);
        return ok ? JNI_TRUE : JNI_FALSE;
    }
cleanup_copy_target_root:
    env->ReleaseStringUTFChars(target_root, target_root_raw);
cleanup_copy_source_relative:
    env->ReleaseStringUTFChars(source_relative, source_relative_raw);
cleanup_copy_source_root:
    env->ReleaseStringUTFChars(source_root, source_root_raw);
    return JNI_FALSE;
}
#endif  // !MMRL_HOST_NATIVE_CONTRACT
