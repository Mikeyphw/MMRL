#pragma once

#include <sys/stat.h>

namespace mmrl::safe_file {
int safe_open_at(const char* root, const char* relative, int flags, mode_t mode);
bool stat_at_no_follow(const char* root, const char* relative, struct stat* out);
bool safe_delete_at(const char* root, const char* relative);
bool safe_mkdir_at(const char* root, const char* relative, bool recursive);
bool safe_create_at(const char* root, const char* relative);
bool safe_rename_at(
        const char* source_root,
        const char* source_relative,
        const char* target_root,
        const char* target_relative);
bool safe_copy_at(
        const char* source_root,
        const char* source_relative,
        const char* target_root,
        const char* target_relative,
        bool overwrite);
}
