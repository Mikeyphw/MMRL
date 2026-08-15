#include <kernelsu/ksu.hpp>
#include <logging.hpp>

#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>
#include <dirent.h>
#include <linux/ioctl.h>

#include <cerrno>
#include <climits>
#include <linux/limits.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

namespace {

constexpr uint32_t KSU_INSTALL_MAGIC1 = 0xDEADBEEF;
constexpr uint32_t KSU_INSTALL_MAGIC2 = 0xCAFEBABE;
constexpr uint32_t KSU_UAPI_V4_PROFILE = 2;
constexpr uint32_t KSU_GET_INFO_FLAG_LKM = 1U << 0;
constexpr uint32_t KSU_GET_INFO_FLAG_MANAGER = 1U << 1;
constexpr uint32_t KSU_FEATURE_SU_COMPAT = 0;

struct CurrentRootProfile {
    int32_t uid;
    int32_t gid;
    uint32_t groups_count;
    int32_t groups[KSU_MAX_GROUPS];
    struct {
        uint64_t effective;
        uint64_t permitted;
        uint64_t inheritable;
    } capabilities;
    char selinux_domain[KSU_SELINUX_DOMAIN];
    int32_t namespaces;
    uint64_t flags;
};

struct CurrentNonRootProfile { bool umount_modules; };
struct CurrentAppProfile {
    uint32_t version;
    char key[KSU_MAX_PACKAGE_NAME];
    int32_t curr_uid;
    bool allow_su;
    union {
        struct {
            bool use_default;
            char template_name[KSU_MAX_PACKAGE_NAME];
            CurrentRootProfile profile;
        } rp_config;
        struct {
            bool use_default;
            CurrentNonRootProfile profile;
        } nrp_config;
    };
};

struct LegacyRootProfile {
    int32_t uid;
    int32_t gid;
    int32_t groups_count;
    int32_t groups[KSU_MAX_GROUPS];
    struct {
        uint64_t effective;
        uint64_t permitted;
        uint64_t inheritable;
    } capabilities;
    char selinux_domain[KSU_SELINUX_DOMAIN];
    int32_t namespaces;
};
struct LegacyNonRootProfile { bool umount_modules; };
struct LegacyAppProfile {
    uint32_t version;
    char key[KSU_MAX_PACKAGE_NAME];
    int32_t current_uid;
    bool allow_su;
    union {
        struct {
            bool use_default;
            char template_name[KSU_MAX_PACKAGE_NAME];
            LegacyRootProfile profile;
        } rp_config;
        struct {
            bool use_default;
            LegacyNonRootProfile profile;
        } nrp_config;
    };
};

struct KsuGetInfoCmd {
    uint32_t version;
    uint32_t flags;
    uint32_t features;
    uint32_t uapi_version;
};
struct KsuCheckSafemodeCmd { uint8_t in_safe_mode; };
struct KsuLegacyAllowListCmd {
    uint32_t uids[128];
    uint32_t count;
    uint8_t allow;
};
struct KsuNewAllowListHeader {
    uint16_t count;
    uint16_t total_count;
};
struct KsuUidShouldUmountCmd {
    uint32_t uid;
    uint8_t should_umount;
};
struct KsuGetAppProfileCmd { CurrentAppProfile profile; };
struct KsuSetAppProfileCmd { CurrentAppProfile profile; };
struct KsuGetFeatureCmd {
    uint32_t feature_id;
    uint64_t value;
    uint8_t supported;
};
struct KsuSetFeatureCmd {
    uint32_t feature_id;
    uint64_t value;
};
struct KsuSetSepolicyCmd {
    uint64_t data_len;
    uint64_t data;
};

constexpr unsigned long KSU_IOCTL_GRANT_ROOT = _IOC(_IOC_NONE, 'K', 1, 0);
constexpr unsigned long KSU_IOCTL_GET_INFO = _IOR('K', 2, KsuGetInfoCmd);
constexpr unsigned long KSU_IOCTL_GET_INFO_LEGACY = _IOC(_IOC_READ, 'K', 2, 0);
constexpr unsigned long KSU_IOCTL_SET_SEPOLICY = _IOC(_IOC_READ | _IOC_WRITE, 'K', 4, 0);
constexpr unsigned long KSU_IOCTL_CHECK_SAFEMODE = _IOC(_IOC_READ, 'K', 5, 0);
constexpr unsigned long KSU_IOCTL_GET_ALLOW_LIST = _IOC(_IOC_READ | _IOC_WRITE, 'K', 6, 0);
constexpr unsigned long KSU_IOCTL_NEW_GET_ALLOW_LIST = _IOWR('K', 6, KsuNewAllowListHeader);
constexpr unsigned long KSU_IOCTL_UID_SHOULD_UMOUNT = _IOC(_IOC_READ | _IOC_WRITE, 'K', 9, 0);
constexpr unsigned long KSU_IOCTL_GET_APP_PROFILE = _IOC(_IOC_READ | _IOC_WRITE, 'K', 11, 0);
constexpr unsigned long KSU_IOCTL_SET_APP_PROFILE = _IOC(_IOC_WRITE, 'K', 12, 0);
constexpr unsigned long KSU_IOCTL_GET_FEATURE = _IOC(_IOC_READ | _IOC_WRITE, 'K', 13, 0);
constexpr unsigned long KSU_IOCTL_SET_FEATURE = _IOC(_IOC_WRITE, 'K', 14, 0);

int g_driver_fd = -2; // -2: uninitialized, -1: unavailable
KsuGetInfoCmd g_info = {};
bool g_info_attempted = false;
bool g_legacy_lkm = false;

int scan_driver_fd() {
    DIR* dir = opendir("/proc/self/fd");
    if (!dir) return -1;
    int found = -1;
    dirent* de;
    char link_path[64];
    char target[PATH_MAX];
    while ((de = readdir(dir)) != nullptr) {
        if (de->d_name[0] == '.') continue;
        char* end = nullptr;
        long raw = strtol(de->d_name, &end, 10);
        if (!de->d_name[0] || !end || *end != '\0' || raw < 0 || raw > INT_MAX) continue;
        int n = snprintf(link_path, sizeof(link_path), "/proc/self/fd/%s", de->d_name);
        if (n <= 0 || static_cast<size_t>(n) >= sizeof(link_path)) continue;
        ssize_t len = readlink(link_path, target, sizeof(target) - 1);
        if (len < 0) continue;
        target[len] = '\0';
        if (strstr(target, "[ksu_driver]") != nullptr) {
            found = static_cast<int>(raw);
            break;
        }
    }
    closedir(dir);
    return found;
}

int init_driver_fd() {
    if (g_driver_fd != -2) return g_driver_fd;
    int fd = scan_driver_fd();
    if (fd < 0) {
        int installed = -1;
#ifdef SYS_reboot
        // Current KernelSU installs a per-process driver descriptor through this guarded reboot
        // supercall. On kernels/forks that do not recognize it, it simply fails and we fall back.
        syscall(SYS_reboot, KSU_INSTALL_MAGIC1, KSU_INSTALL_MAGIC2, 0, &installed);
#endif
        if (installed >= 0) fd = installed;
    }
    g_driver_fd = fd;
    return g_driver_fd;
}

int current_ioctl(unsigned long request, void* arg) {
    const int fd = init_driver_fd();
    if (fd < 0) {
        errno = ENODEV;
        return -1;
    }
    return ioctl(fd, request, arg);
}

bool current_info(KsuGetInfoCmd* out) {
    if (!out) return false;
    if (!g_info_attempted) {
        g_info_attempted = true;
        KsuGetInfoCmd info = {};
        if (current_ioctl(KSU_IOCTL_GET_INFO, &info) < 0) {
            // Transitional current-driver builds exposed the legacy GET_INFO ioctl number.
            info = {};
            if (current_ioctl(KSU_IOCTL_GET_INFO_LEGACY, &info) < 0) {
                g_info = {};
            }
        }
        g_info = info;
    }
    *out = g_info;
    return out->version > 0;
}

bool current_uapi_v4_profile() {
    KsuGetInfoCmd info = {};
    return current_info(&info) && info.uapi_version >= KSU_UAPI_V4_PROFILE;
}

void neutral_to_current(const app_profile& src, CurrentAppProfile* dst) {
    memset(dst, 0, sizeof(*dst));
    dst->version = KSU_APP_PROFILE_VER;
    memcpy(dst->key, src.key, sizeof(dst->key));
    dst->key[sizeof(dst->key) - 1] = '\0';
    dst->curr_uid = src.current_uid;
    dst->allow_su = src.allow_su;
    if (src.allow_su) {
        dst->rp_config.use_default = src.rp_config.use_default;
        memcpy(dst->rp_config.template_name, src.rp_config.template_name, sizeof(dst->rp_config.template_name));
        dst->rp_config.template_name[sizeof(dst->rp_config.template_name) - 1] = '\0';
        const root_profile& in = src.rp_config.profile;
        CurrentRootProfile& out = dst->rp_config.profile;
        out.uid = in.uid;
        out.gid = in.gid;
        out.groups_count = in.groups_count > KSU_MAX_GROUPS ? KSU_MAX_GROUPS : in.groups_count;
        memcpy(out.groups, in.groups, sizeof(out.groups));
        out.capabilities.effective = in.capabilities.effective;
        out.capabilities.permitted = in.capabilities.permitted;
        out.capabilities.inheritable = in.capabilities.inheritable;
        memcpy(out.selinux_domain, in.selinux_domain, sizeof(out.selinux_domain));
        out.selinux_domain[sizeof(out.selinux_domain) - 1] = '\0';
        out.namespaces = in.namespaces;
        out.flags = in.flags;
    } else {
        dst->nrp_config.use_default = src.nrp_config.use_default;
        dst->nrp_config.profile.umount_modules = src.nrp_config.profile.umount_modules;
    }
}

void current_to_neutral(const CurrentAppProfile& src, app_profile* dst) {
    memset(dst, 0, sizeof(*dst));
    dst->version = src.version;
    memcpy(dst->key, src.key, sizeof(dst->key));
    dst->key[sizeof(dst->key) - 1] = '\0';
    dst->current_uid = src.curr_uid;
    dst->allow_su = src.allow_su;
    if (src.allow_su) {
        dst->rp_config.use_default = src.rp_config.use_default;
        memcpy(dst->rp_config.template_name, src.rp_config.template_name, sizeof(dst->rp_config.template_name));
        dst->rp_config.template_name[sizeof(dst->rp_config.template_name) - 1] = '\0';
        const CurrentRootProfile& in = src.rp_config.profile;
        root_profile& out = dst->rp_config.profile;
        out.uid = in.uid;
        out.gid = in.gid;
        out.groups_count = in.groups_count > KSU_MAX_GROUPS ? KSU_MAX_GROUPS : in.groups_count;
        memcpy(out.groups, in.groups, sizeof(out.groups));
        out.capabilities.effective = in.capabilities.effective;
        out.capabilities.permitted = in.capabilities.permitted;
        out.capabilities.inheritable = in.capabilities.inheritable;
        memcpy(out.selinux_domain, in.selinux_domain, sizeof(out.selinux_domain));
        out.selinux_domain[sizeof(out.selinux_domain) - 1] = '\0';
        out.namespaces = in.namespaces;
        out.flags = in.flags;
    } else {
        dst->nrp_config.use_default = src.nrp_config.use_default;
        dst->nrp_config.profile.umount_modules = src.nrp_config.profile.umount_modules;
    }
}

void neutral_to_legacy(const app_profile& src, LegacyAppProfile* dst) {
    memset(dst, 0, sizeof(*dst));
    dst->version = KSU_LEGACY_APP_PROFILE_VER;
    memcpy(dst->key, src.key, sizeof(dst->key));
    dst->key[sizeof(dst->key) - 1] = '\0';
    dst->current_uid = src.current_uid;
    dst->allow_su = src.allow_su;
    if (src.allow_su) {
        dst->rp_config.use_default = src.rp_config.use_default;
        memcpy(dst->rp_config.template_name, src.rp_config.template_name, sizeof(dst->rp_config.template_name));
        dst->rp_config.template_name[sizeof(dst->rp_config.template_name) - 1] = '\0';
        dst->rp_config.profile.uid = src.rp_config.profile.uid;
        dst->rp_config.profile.gid = src.rp_config.profile.gid;
        dst->rp_config.profile.groups_count = static_cast<int32_t>(src.rp_config.profile.groups_count > KSU_MAX_GROUPS ? KSU_MAX_GROUPS : src.rp_config.profile.groups_count);
        memcpy(dst->rp_config.profile.groups, src.rp_config.profile.groups, sizeof(dst->rp_config.profile.groups));
        dst->rp_config.profile.capabilities.effective = src.rp_config.profile.capabilities.effective;
        dst->rp_config.profile.capabilities.permitted = src.rp_config.profile.capabilities.permitted;
        dst->rp_config.profile.capabilities.inheritable = src.rp_config.profile.capabilities.inheritable;
        memcpy(dst->rp_config.profile.selinux_domain, src.rp_config.profile.selinux_domain, sizeof(dst->rp_config.profile.selinux_domain));
        dst->rp_config.profile.selinux_domain[sizeof(dst->rp_config.profile.selinux_domain) - 1] = '\0';
        dst->rp_config.profile.namespaces = src.rp_config.profile.namespaces;
    } else {
        dst->nrp_config.use_default = src.nrp_config.use_default;
        dst->nrp_config.profile.umount_modules = src.nrp_config.profile.umount_modules;
    }
}

void legacy_to_neutral(const LegacyAppProfile& src, app_profile* dst) {
    memset(dst, 0, sizeof(*dst));
    dst->version = src.version;
    memcpy(dst->key, src.key, sizeof(dst->key));
    dst->key[sizeof(dst->key) - 1] = '\0';
    dst->current_uid = src.current_uid;
    dst->allow_su = src.allow_su;
    if (src.allow_su) {
        dst->rp_config.use_default = src.rp_config.use_default;
        memcpy(dst->rp_config.template_name, src.rp_config.template_name, sizeof(dst->rp_config.template_name));
        dst->rp_config.template_name[sizeof(dst->rp_config.template_name) - 1] = '\0';
        dst->rp_config.profile.uid = src.rp_config.profile.uid;
        dst->rp_config.profile.gid = src.rp_config.profile.gid;
        const int32_t count = src.rp_config.profile.groups_count;
        dst->rp_config.profile.groups_count = count <= 0 ? 0U : static_cast<uint32_t>(count > KSU_MAX_GROUPS ? KSU_MAX_GROUPS : count);
        memcpy(dst->rp_config.profile.groups, src.rp_config.profile.groups, sizeof(dst->rp_config.profile.groups));
        dst->rp_config.profile.capabilities.effective = src.rp_config.profile.capabilities.effective;
        dst->rp_config.profile.capabilities.permitted = src.rp_config.profile.capabilities.permitted;
        dst->rp_config.profile.capabilities.inheritable = src.rp_config.profile.capabilities.inheritable;
        memcpy(dst->rp_config.profile.selinux_domain, src.rp_config.profile.selinux_domain, sizeof(dst->rp_config.profile.selinux_domain));
        dst->rp_config.profile.selinux_domain[sizeof(dst->rp_config.profile.selinux_domain) - 1] = '\0';
        dst->rp_config.profile.namespaces = src.rp_config.profile.namespaces;
        dst->rp_config.profile.flags = 0;
    } else {
        dst->nrp_config.use_default = src.nrp_config.use_default;
        dst->nrp_config.profile.umount_modules = src.nrp_config.profile.umount_modules;
    }
}

size_t bounded_strlen(const char* value, size_t max_len) {
    if (!value) return 0;
    size_t n = 0;
    while (n < max_len && value[n] != '\0') ++n;
    return n;
}

} // namespace

bool ksuctl(int cmd, void* arg1, void* arg2) {
    int32_t result = 0;
    prctl(KERNEL_SU_OPTION, cmd, arg1, arg2, &result);
    return static_cast<uint32_t>(result) == KERNEL_SU_OPTION;
}

bool grant_root() {
    KsuGetInfoCmd info = {};
    if (current_info(&info)) return current_ioctl(KSU_IOCTL_GRANT_ROOT, nullptr) == 0;
    return ksuctl(CMD_GRANT_ROOT, nullptr, nullptr);
}

bool become_manager(const char *pkg) {
    if (!pkg || *pkg == '\0') return false;

    // Current KernelSU reports manager authorization explicitly in GET_INFO. Merely reaching the
    // driver is not sufficient: root-capable non-manager processes may still obtain basic info.
    KsuGetInfoCmd info = {};
    if (current_info(&info) && info.uapi_version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_MANAGER) != 0;
    }

    // Historical/forked prctl implementations (and transitional UAPI-0 builds) still require the
    // package data path.
    char param[128];
    uid_t uid = getuid();
    uint32_t userId = uid / 100000;
    const int written = userId == 0
        ? snprintf(param, sizeof(param), "/data/data/%s", pkg)
        : snprintf(param, sizeof(param), "/data/user/%u/%s", userId, pkg);
    if (written < 0 || static_cast<size_t>(written) >= sizeof(param)) return false;
    return ksuctl(CMD_BECOME_MANAGER, param, nullptr);
}

int get_version() {
    KsuGetInfoCmd info = {};
    if (current_info(&info)) return static_cast<int>(info.version);
    int32_t version = -1;
    int32_t flags = 0;
    if (ksuctl(CMD_GET_VERSION, &version, &flags)) {
        g_legacy_lkm = (flags & 0x1) != 0;
        return version;
    }
    return -1;
}

namespace ksu_compat {
bool validate_allowlist_counts(uint32_t requested_capacity, uint32_t returned_count, uint32_t total_count) {
    return requested_capacity <= UINT16_MAX && returned_count <= requested_capacity && returned_count <= total_count;
}

size_t sepolicy_argument_count(uint32_t cmd) {
    switch (cmd) {
        case 1: return 4; // normal perm
        case 2: return 5; // xperm
        case 3: return 1; // type state
        case 4: return 2; // type
        case 5: return 2; // type attr
        case 6: return 1; // attr
        case 7: return 5; // transition
        case 8: return 4; // change
        case 9: return 3; // genfscon
        default: return 0;
    }
}

bool serialize_sepolicy(const FfiPolicy& policy, unsigned char* out, size_t capacity, size_t* size) {
    if (!out || !size) return false;
    const size_t argc = sepolicy_argument_count(policy.cmd);
    if (argc == 0) return false;
    const char* args[7] = { policy.sepol1, policy.sepol2, policy.sepol3, policy.sepol4, policy.sepol5, policy.sepol6, policy.sepol7 };
    size_t pos = 0;
    auto append_u32 = [&](uint32_t value) -> bool {
        if (capacity - pos < sizeof(value)) return false;
        memcpy(out + pos, &value, sizeof(value));
        pos += sizeof(value);
        return true;
    };
    if (!append_u32(policy.cmd) || !append_u32(policy.subcmd)) return false;
    for (size_t i = 0; i < argc; ++i) {
        const size_t len = bounded_strlen(args[i], 128);
        if (args[i] && len == 128) return false;
        if (len > UINT32_MAX || !append_u32(static_cast<uint32_t>(len))) return false;
        if (capacity - pos < len + 1) return false;
        if (len > 0) memcpy(out + pos, args[i], len);
        pos += len;
        out[pos++] = '\0';
    }
    *size = pos;
    return true;
}
} // namespace ksu_compat

bool get_allow_list(int *uids, int capacity, int *size) {
    if (!uids || !size || capacity < 0 || capacity > UINT16_MAX) return false;
    KsuGetInfoCmd info = {};
    if (current_info(&info)) {
        KsuNewAllowListHeader probe = {};
        if (current_ioctl(KSU_IOCTL_NEW_GET_ALLOW_LIST, &probe) == 0) {
            if (probe.total_count > static_cast<uint32_t>(capacity)) return false;
            const uint16_t request_count = probe.total_count;
            // One aligned u32 contains the four-byte header; following words are UID storage.
            std::vector<uint32_t> storage(1U + static_cast<size_t>(request_count), 0U);
            auto* cmd = reinterpret_cast<KsuNewAllowListHeader*>(storage.data());
            cmd->count = request_count;
            if (current_ioctl(KSU_IOCTL_NEW_GET_ALLOW_LIST, cmd) < 0) return false;
            if (!ksu_compat::validate_allowlist_counts(
                    static_cast<uint32_t>(capacity), cmd->count, cmd->total_count)) return false;
            for (uint16_t i = 0; i < cmd->count; ++i) {
                uint32_t uid = 0;
                std::memcpy(&uid, storage.data() + 1U + i, sizeof(uid));
                uids[i] = static_cast<int>(uid);
            }
            *size = cmd->count;
            return true;
        }

        // Compatibility for ioctl-era kernels predating the variable-length list. The kernel-side
        // buffer is fixed at 128, so validate the returned count before copying anything.
        KsuLegacyAllowListCmd legacy = {};
        legacy.allow = 1;
        if (current_ioctl(KSU_IOCTL_GET_ALLOW_LIST, &legacy) < 0) return false;
        if (legacy.count > 128U || legacy.count > static_cast<uint32_t>(capacity)) return false;
        for (uint32_t i = 0; i < legacy.count; ++i) uids[i] = static_cast<int>(legacy.uids[i]);
        *size = static_cast<int>(legacy.count);
        return true;
    }
    int legacy_size = capacity;
    if (!ksuctl(CMD_GET_SU_LIST, uids, &legacy_size)) return false;
    if (legacy_size < 0 || legacy_size > capacity) return false;
    *size = legacy_size;
    return true;
}

bool is_safe_mode() {
    KsuGetInfoCmd info = {};
    if (current_info(&info)) {
        KsuCheckSafemodeCmd cmd = {};
        return current_ioctl(KSU_IOCTL_CHECK_SAFEMODE, &cmd) == 0 && cmd.in_safe_mode != 0;
    }
    return ksuctl(CMD_CHECK_SAFEMODE, nullptr, nullptr);
}

bool is_lkm_mode() {
    KsuGetInfoCmd info = {};
    if (current_info(&info)) return (info.flags & KSU_GET_INFO_FLAG_LKM) != 0;
    return g_legacy_lkm;
}

bool uid_should_umount(int uid) {
    KsuGetInfoCmd info = {};
    if (current_info(&info)) {
        KsuUidShouldUmountCmd cmd = { static_cast<uint32_t>(uid), 0 };
        return current_ioctl(KSU_IOCTL_UID_SHOULD_UMOUNT, &cmd) == 0 && cmd.should_umount != 0;
    }
    bool should = false;
    return ksuctl(CMD_IS_UID_SHOULD_UMOUNT, reinterpret_cast<void *>(static_cast<intptr_t>(uid)), &should) && should;
}

bool set_su_enabled(bool enabled) {
    KsuGetInfoCmd info = {};
    if (current_info(&info)) {
        KsuSetFeatureCmd cmd = { KSU_FEATURE_SU_COMPAT, enabled ? 1ULL : 0ULL };
        return current_ioctl(KSU_IOCTL_SET_FEATURE, &cmd) == 0;
    }
    return ksuctl(CMD_ENABLE_SU, reinterpret_cast<void *>(enabled ? 1 : 0), nullptr);
}

bool is_su_enabled() {
    KsuGetInfoCmd info = {};
    if (current_info(&info)) {
        KsuGetFeatureCmd cmd = { KSU_FEATURE_SU_COMPAT, 0, 0 };
        return current_ioctl(KSU_IOCTL_GET_FEATURE, &cmd) == 0 && cmd.supported != 0 && cmd.value != 0;
    }
    bool enabled = true;
    return ksuctl(CMD_IS_SU_ENABLED, &enabled, nullptr) && enabled;
}

bool set_app_profile(const app_profile *profile) {
    if (!profile) return false;
    if (current_uapi_v4_profile()) {
        KsuSetAppProfileCmd cmd = {};
        neutral_to_current(*profile, &cmd.profile);
        return current_ioctl(KSU_IOCTL_SET_APP_PROFILE, &cmd) == 0;
    }
    LegacyAppProfile legacy = {};
    neutral_to_legacy(*profile, &legacy);
    return ksuctl(CMD_SET_APP_PROFILE, &legacy, nullptr);
}

bool get_app_profile(p_key_t key, app_profile *profile) {
    if (!profile) return false;
    if (current_uapi_v4_profile()) {
        KsuGetAppProfileCmd cmd = {};
        neutral_to_current(*profile, &cmd.profile);
        memcpy(cmd.profile.key, key, sizeof(cmd.profile.key));
        cmd.profile.key[sizeof(cmd.profile.key) - 1] = '\0';
        if (current_ioctl(KSU_IOCTL_GET_APP_PROFILE, &cmd) < 0) return false;
        current_to_neutral(cmd.profile, profile);
        return true;
    }
    LegacyAppProfile legacy = {};
    neutral_to_legacy(*profile, &legacy);
    memcpy(legacy.key, key, sizeof(legacy.key));
    legacy.key[sizeof(legacy.key) - 1] = '\0';
    if (!ksuctl(CMD_GET_APP_PROFILE, &legacy, nullptr)) return false;
    legacy_to_neutral(legacy, profile);
    return true;
}

bool ksu_set_policy(const FfiPolicy* policy) {
    if (!policy) return false;
    KsuGetInfoCmd info = {};
    if (current_info(&info)) {
        unsigned char payload[1024] = {};
        size_t payload_size = 0;
        if (!ksu_compat::serialize_sepolicy(*policy, payload, sizeof(payload), &payload_size)) return false;
        KsuSetSepolicyCmd cmd = { static_cast<uint64_t>(payload_size), reinterpret_cast<uint64_t>(payload) };
        return current_ioctl(KSU_IOCTL_SET_SEPOLICY, &cmd) == 0;
    }
    return ksuctl(CMD_SET_SEPOLICY, nullptr, const_cast<FfiPolicy*>(policy));
}
