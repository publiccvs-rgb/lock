/*
 * Windows (win32/win64) machine-dependent JNI typedefs. This is the standard OpenJDK content for this header -
 * the rest of jni.h/jvmti.h are platform-independent and come verbatim from a real JDK install; only this one
 * header differs by OS, and no Windows JDK is available in this build environment to source it from directly.
 */
#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_

#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall

typedef long jint;
typedef __int64 jlong;
typedef signed char jbyte;

#endif
