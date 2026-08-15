#include <kernelsu/ksu.hpp>

#include <cassert>
#include <cstdint>
#include <cstring>

namespace {
uint32_t read_u32(const unsigned char* p) {
    uint32_t value = 0;
    std::memcpy(&value, p, sizeof(value));
    return value;
}
}

int main() {
    assert(ksu_compat::validate_allowlist_counts(65535, 0, 0));
    assert(ksu_compat::validate_allowlist_counts(65535, 4, 9));
    assert(!ksu_compat::validate_allowlist_counts(65536, 0, 0));
    assert(!ksu_compat::validate_allowlist_counts(4, 5, 5));
    assert(!ksu_compat::validate_allowlist_counts(4, 4, 3));

    FfiPolicy allow{};
    allow.cmd = 1;
    allow.subcmd = 7;
    allow.sepol1 = "source";
    allow.sepol2 = "target";
    allow.sepol3 = "file";
    allow.sepol4 = "read";

    unsigned char payload[256] = {};
    size_t payload_size = 0;
    assert(ksu_compat::serialize_sepolicy(allow, payload, sizeof(payload), &payload_size));
    assert(payload_size > 8);
    assert(read_u32(payload) == 1);
    assert(read_u32(payload + 4) == 7);
    assert(read_u32(payload + 8) == 6);
    assert(std::memcmp(payload + 12, "source\0", 7) == 0);

    FfiPolicy invalid{};
    invalid.cmd = 99;
    assert(!ksu_compat::serialize_sepolicy(invalid, payload, sizeof(payload), &payload_size));

    char unterminated[128];
    std::memset(unterminated, 'x', sizeof(unterminated));
    FfiPolicy too_long{};
    too_long.cmd = 3;
    too_long.sepol1 = unterminated;
    assert(!ksu_compat::serialize_sepolicy(too_long, payload, sizeof(payload), &payload_size));

    return 0;
}
