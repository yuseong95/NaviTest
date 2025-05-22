// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
#include <filesystem>
#include <iostream>
#include <jni.h>
#include <string>

#include "GenieWrapper.hpp"
#include <cstdlib>

extern "C" JNIEXPORT jlong JNICALL Java_com_capstone_navitest_GenieWrapper_loadModel(JNIEnv* env,
                                                                                   jobject /* this */,
                                                                                   jstring model_dir_path,
                                                                                   jstring htp_config_path)
{
    setenv("GENIE_BACKEND", "cpu_init_htp_run", 1);
    /* ②(선택) 문제가 또 나올 때 원인을 바로 알기 위한 Verbose 모드 */
    setenv("GENIE_QNN_VERBOSE", "1", 1);
    try
    {
        std::string model_dir = std::string(env->GetStringUTFChars(model_dir_path, 0));
        std::string htp_config = std::string(env->GetStringUTFChars(htp_config_path, 0));
        std::filesystem::path model_config_path = std::filesystem::path(model_dir) / "genie-config.json";
        std::filesystem::path tokenizer_path = std::filesystem::path(model_dir) / "tokenizer.json";

        App::GenieWrapper* chatApp =
                new App::GenieWrapper(model_config_path.string(), model_dir, htp_config, tokenizer_path.string());
        return reinterpret_cast<jlong>(chatApp);
    }
    catch (std::exception& e)
    {
        jclass exception_cls = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exception_cls, e.what());
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_capstone_navitest_GenieWrapper_getResponseForPrompt(JNIEnv* env,
                                                                                             jobject /* this */,
                                                                                             jlong genie_wrapper_handle,
                                                                                             jstring user_question,
                                                                                             jobject callback)
{
    try
    {
        // Get callback method
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onNewStringMethod = env->GetMethodID(callbackClass, "onNewString", "(Ljava/lang/String;)V");

        std::string user_input = env->GetStringUTFChars(user_question, 0);

        // Get response from Genie
        App::GenieWrapper* myClass = reinterpret_cast<App::GenieWrapper*>(genie_wrapper_handle);
        auto response = myClass->GetResponseForPrompt(user_input, env, callback, onNewStringMethod);
    }
    catch (std::exception& e)
    {
        jclass exception_cls = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exception_cls, e.what());
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_capstone_navitest_GenieWrapper_freeModel(JNIEnv* env,
                                                                                  jobject /* this */,
                                                                                  jlong genie_wrapper_handle)
{
    try
    {
        App::GenieWrapper* genie_wrapper = reinterpret_cast<App::GenieWrapper*>(genie_wrapper_handle);
        delete genie_wrapper;
    }
    catch (std::exception& e)
    {
        jclass exception_cls = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exception_cls, e.what());
    }
}
