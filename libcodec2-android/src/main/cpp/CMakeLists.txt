# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10)
project(libcodec)
set(main_DIR ${CMAKE_SOURCE_DIR})
add_library(libcodec2 SHARED IMPORTED)
set_target_properties(libcodec2 PROPERTIES IMPORTED_LOCATION
        ${main_DIR}/../../../build/imported-lib/${ANDROID_ABI}/libcodec2.so)

include_directories(${main_DIR}/codec2/)


# Searches for a specified prebuilt library and stores the path as a
## variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.


add_library( # Sets the name of the library.
        Codec2JNI
        ## Sets the library as a shared library.
        SHARED
        ## Provides a relative path to your source file(s).
        Codec2JNI.cpp)

find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
target_link_libraries(
        Codec2JNI
        libcodec2
        ${log-lib}
        "-Wl,--build-id=none")
