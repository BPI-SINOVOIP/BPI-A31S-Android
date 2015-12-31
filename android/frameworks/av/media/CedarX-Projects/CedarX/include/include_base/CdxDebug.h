#ifndef CDX_DEBUG_H
#define CDX_DEBUG_H
#include <sys/types.h>
#include <cutils/log.h>

#define CDX_LOG(level, fmt, arg...) \
    LOG_PRI(level, LOG_TAG,             \
        "<%s:%u>"fmt, strrchr(__FILE__, '/') + 1, __LINE__, ##arg)

#define CDX_LOGE(fmt, arg...)               \
    CDX_LOG(ANDROID_LOG_ERROR, fmt, ##arg)

#define CDX_LOGI(fmt, arg...)               \
    CDX_LOG(ANDROID_LOG_INFO, fmt, ##arg)

#define CDX_TRACE() \
    CDX_LOGI("<%s:%u> tid(%d)", strrchr(__FILE__, '/') + 1, __LINE__, gettid())

#define CDX_CHECK(e) \
    LOG_ALWAYS_FATAL_IF(                        \
            !(e),                               \
            "<%s:%d>CDX_CHECK(%s) failed.",     \
            strrchr(__FILE__, '/') + 1, __LINE__, #e)             \

#ifdef __cplusplus
extern "C"
{
#endif

void CdxCallStack(void);


#ifdef __cplusplus
}
#endif

#endif
