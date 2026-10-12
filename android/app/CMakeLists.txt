# Android packaging for unmodified upstream tinc. No engine patches are applied.
cmake_minimum_required(VERSION 3.22)
project(tinc_android C)
include(ExternalProject)

find_program(MESON meson REQUIRED)
find_program(NINJA ninja REQUIRED)

if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(TINC_CPU_FAMILY aarch64)
  set(TINC_CPU aarch64)
elseif(ANDROID_ABI STREQUAL "x86_64")
  set(TINC_CPU_FAMILY x86_64)
  set(TINC_CPU x86_64)
endif()

configure_file(src/main/c/android.ini.in android.ini @ONLY)
ExternalProject_Add(tinc
  URL https://codeload.github.com/gsliepen/tinc/tar.gz/211e3dfaef32d8736962e25f0b096dad951b7104
  CONFIGURE_COMMAND ${MESON} setup <BINARY_DIR> <SOURCE_DIR>
    --cross-file ${CMAKE_CURRENT_BINARY_DIR}/android.ini
    --buildtype=release --prefix=/usr
    -Dcrypto=nolegacy -Ddocs=disabled -Dtests=disabled -Dsystemd=disabled
    -Dcurses=disabled -Dreadline=disabled -Dlzo=disabled -Dlz4=disabled -Dzlib=disabled
  BUILD_COMMAND ${MESON} compile -C <BINARY_DIR>
  INSTALL_COMMAND ""
)

# AGP can clear its output directory while retaining ExternalProject's install
# stamp. Stage the executables on every native build, even when Meson is cached.
ExternalProject_Get_Property(tinc BINARY_DIR)
add_custom_target(package_tinc
  COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
  COMMAND ${CMAKE_COMMAND} -E copy_if_different ${BINARY_DIR}/src/tinc ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libtinc.so
  COMMAND ${CMAKE_COMMAND} -E copy_if_different ${BINARY_DIR}/src/tincd ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libtincd.so
  BYPRODUCTS ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libtinc.so ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libtincd.so
)
add_dependencies(package_tinc tinc)

# AGP collects the staged executables alongside this native library.
add_library(main SHARED src/main/c/main.c)
add_dependencies(main package_tinc)
