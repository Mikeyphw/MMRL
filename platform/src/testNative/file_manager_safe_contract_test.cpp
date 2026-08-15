#include "file_manager_safe.hpp"

#include <cassert>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <filesystem>
#include <fstream>
#include <string>
#include <unistd.h>
#include <vector>

namespace fs = std::filesystem;
using namespace mmrl::safe_file;

static std::string temp_dir(const char* prefix) {
    std::string pattern = std::string("/tmp/") + prefix + "XXXXXX";
    std::vector<char> buffer(pattern.begin(), pattern.end());
    buffer.push_back('\0');
    char* out = mkdtemp(buffer.data());
    assert(out != nullptr);
    return out;
}

static std::string read_file(const fs::path& path) {
    std::ifstream input(path, std::ios::binary);
    return std::string(std::istreambuf_iterator<char>(input), {});
}

int main() {
    const std::string root = temp_dir("mmrl-safe-root-");
    const std::string outside = temp_dir("mmrl-safe-outside-");
    fs::create_directories(fs::path(root) / "a" / "b");
    fs::create_directories(fs::path(outside) / "escape");
    std::ofstream(fs::path(root) / "a" / "b" / "source.txt") << "payload";
    std::ofstream(fs::path(outside) / "escape" / "victim.txt") << "outside";

    // Production open path performs actual I/O and rejects intermediate/final symlinks.
    int fd = safe_open_at(root.c_str(), "a/b/source.txt", O_RDONLY, 0);
    assert(fd >= 0);
    char content[8] = {};
    assert(read(fd, content, 7) == 7);
    close(fd);
    assert(std::string(content, 7) == "payload");

    assert(symlink((fs::path(outside) / "escape").c_str(), (fs::path(root) / "jump").c_str()) == 0);
    assert(safe_open_at(root.c_str(), "jump/victim.txt", O_RDONLY, 0) < 0);
    assert(!safe_create_at(root.c_str(), "jump/created.txt"));
    assert(!safe_mkdir_at(root.c_str(), "jump/newdir", false));
    assert(!safe_delete_at(root.c_str(), "jump/victim.txt"));
    assert(fs::exists(fs::path(outside) / "escape" / "victim.txt"));

    // Copy/rename destinations are resolved through no-follow parents as well.
    assert(!safe_copy_at(root.c_str(), "a/b/source.txt", root.c_str(), "jump/copied.txt", false));
    assert(!safe_rename_at(root.c_str(), "a/b/source.txt", root.c_str(), "jump/moved.txt"));
    assert(fs::exists(fs::path(root) / "a" / "b" / "source.txt"));

    assert(safe_mkdir_at(root.c_str(), "a/new/deep", true));
    assert(safe_create_at(root.c_str(), "a/new/deep/dst.txt"));
    assert(safe_copy_at(root.c_str(), "a/b/source.txt", root.c_str(), "a/new/deep/dst.txt", true));
    assert(read_file(fs::path(root) / "a" / "new" / "deep" / "dst.txt") == "payload");
    assert(safe_rename_at(root.c_str(), "a/new/deep/dst.txt", root.c_str(), "a/new/deep/final.txt"));
    assert(safe_delete_at(root.c_str(), "a/new"));
    assert(!fs::exists(fs::path(root) / "a" / "new"));

    // Final symlink deletion removes the link, never its target.
    assert(safe_delete_at(root.c_str(), "jump"));
    assert(fs::exists(fs::path(outside) / "escape" / "victim.txt"));

    fs::remove_all(root);
    fs::remove_all(outside);
    return 0;
}
