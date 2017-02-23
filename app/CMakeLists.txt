# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

include_directories(src/main/cpp/rtmp/librtmp)

set( MY_CPP_LIST
     src/main/cpp/rtmp/libresrtmp.c
     src/main/cpp/rtmp/libresrtmp.h
     src/main/cpp/rtmp/log.h
     src/main/cpp/rtmp/librtmp/amf.c
     src/main/cpp/rtmp/librtmp/amf.h
     src/main/cpp/rtmp/librtmp/bytes.h
     src/main/cpp/rtmp/librtmp/dh.h
     src/main/cpp/rtmp/librtmp/dhgroups.h
     src/main/cpp/rtmp/librtmp/handshake.h
     src/main/cpp/rtmp/librtmp/hashswf.c
     src/main/cpp/rtmp/librtmp/http.h
     src/main/cpp/rtmp/librtmp/log.c
     src/main/cpp/rtmp/librtmp/log.h
     src/main/cpp/rtmp/librtmp/parseurl.c
     src/main/cpp/rtmp/librtmp/rtmp.c
     src/main/cpp/rtmp/librtmp/rtmp.h
     src/main/cpp/rtmp/librtmp/rtmp_sys.h)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             resrtmp

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             # Associated headers in the same location as their source
             # file are automatically included.
             ${MY_CPP_LIST} )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because system libraries are included in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       resrtmp

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
