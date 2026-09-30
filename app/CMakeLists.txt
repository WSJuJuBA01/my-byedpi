cmake_minimum_required(VERSION 3.22.1)
project("custom_dpi_core")

add_library(custom_dpi_core SHARED
    src/main/cpp/custom_dpi_core.c)

find_library(log-lib log)
target_link_libraries(custom_dpi_core ${log-lib})
