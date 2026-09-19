# The JNI shim only. llama.cpp itself is NOT built here.
#
# WHY NOT. Building llama.cpp as a subproject would add several minutes to every Gradle build and
# would tie this module to a large vendored source tree that is deliberately not committed. The
# libraries are produced once by tools/5-build-llama-android.ps1 and dropped into
# app/src/main/jniLibs/arm64-v8a, which is also where Gradle packages them from. This file builds
# the one small shim and links against them.
#
# A fresh clone therefore needs that script run before assembling. The configure step below says so
# plainly rather than failing later with a linker error nobody can read.

cmake_minimum_required(VERSION 3.22.1)
project(katori_llama LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

foreach(LIB llama ggml ggml-base ggml-cpu)
    if(NOT EXISTS "${PREBUILT_DIR}/lib${LIB}.so")
        message(FATAL_ERROR
            "lib${LIB}.so is missing from ${PREBUILT_DIR}.\n"
            "Run tools/5-build-llama-android.ps1 to produce the llama.cpp libraries, then build "
            "again. They are ~53 MB and deliberately not committed.")
    endif()
    add_library(${LIB} SHARED IMPORTED)
    set_target_properties(${LIB} PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib${LIB}.so")
endforeach()

add_library(katori_llama SHARED katori_llama.cpp)

target_include_directories(katori_llama PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")

find_library(log-lib log)
target_link_libraries(katori_llama llama ggml ggml-base ggml-cpu ${log-lib})
