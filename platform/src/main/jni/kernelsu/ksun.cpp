#include <kernelsu/ksun.hpp>
#include <kernelsu/ksu.hpp>
#include <cstdio>

const char* get_ksun_hook_mode() {
    static char mode[16] = {0};
    mode[0] = '\0';
    mode[sizeof(mode) - 1] = '\0';
    if (!ksuctl(CMD_KSUN_HOOK_MODE, mode, nullptr)) {
        std::snprintf(mode, sizeof(mode), "%s", "Unknown");
    }
    mode[sizeof(mode) - 1] = '\0';
    return mode;
}
