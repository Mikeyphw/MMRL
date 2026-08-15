#include <kernelsu/sukisu.hpp>
#include <kernelsu/ksu.hpp>
#include <cstdio>

bool is_KPM_enable() {
    int enabled = false;
    ksuctl(CMD_ENABLE_KPM, &enabled, nullptr);
    return enabled;
}

bool get_suki_hook_type(char* hook_type, size_t size) {
    if (hook_type == nullptr || size == 0) {
        return false;
    }

    static char cached_hook_type[16] = {0};
    if (cached_hook_type[0] == '\0') {
        cached_hook_type[sizeof(cached_hook_type) - 1] = '\0';
        if (!ksuctl(CMD_HOOK_TYPE, cached_hook_type, nullptr)) {
            std::snprintf(cached_hook_type, sizeof(cached_hook_type), "%s", "Unknown");
        }
        cached_hook_type[sizeof(cached_hook_type) - 1] = '\0';
    }

    std::snprintf(hook_type, size, "%s", cached_hook_type);
    return true;
}