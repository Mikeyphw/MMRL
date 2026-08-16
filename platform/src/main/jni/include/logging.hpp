#ifndef MMRL_LOGGING_HPP
#define MMRL_LOGGING_HPP

#ifndef MMRL_HOST_NATIVE_CONTRACT
#include <android/log.h>
#endif

#define LOG_TAG "MMRL-Native"

#ifdef MMRL_HOST_NATIVE_CONTRACT
#define LOGV(...)  ((void)0)
#define LOGI(...)  ((void)0)
#define LOGD(...)  ((void)0)
#define LOGW(...)  ((void)0)
#define LOGE(...)  ((void)0)
#else
#define LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif

#endif // MMRL_LOGGING_HPP