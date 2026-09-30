#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>
#include <android/log.h>

#define LOG_TAG "CustomDpiCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

void process_and_fragment(int server_fd, char *buffer, ssize_t bytes_read) {
    if (bytes_read > 5 && buffer[0] == 0x16 && buffer[1] == 0x03) {
        LOGI("TLS Client Hello обнаружен! Выполняем фрагментацию SNI...");
        int flag = 1;
        setsockopt(server_fd, IPPROTO_TCP, TCP_NODELAY, (char *)&flag, sizeof(int));

        int split_pos = 5;
        send(server_fd, buffer, split_pos, 0);
        usleep(5000);
        send(server_fd, buffer + split_pos, bytes_read - split_pos, 0);
    } else {
        send(server_fd, buffer, bytes_read, 0);
    }
}

int main(int argc, char *argv[]) {
    LOGI("Custom DPI C-Core запущен!");
    return 0;
}
