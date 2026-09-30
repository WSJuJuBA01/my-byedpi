#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <pthread.h>
#include <android/log.h>

#define LOG_TAG "WSByeDPI_Core"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define PORT 1080

void handle_client(int client_fd) {
    char buffer[8192];
    ssize_t bytes = read(client_fd, buffer, sizeof(buffer));

    if (bytes > 5 && buffer[0] == 0x16 && buffer[1] == 0x03) {
        LOGI("TLS Client Hello обнаружен! Фрагментируем пакет...");

        // Пример разрезки пакета на 2 части (split at position 5)
        int split_pos = 5;

        // В реальном ByeDPI здесь выполняется проксирование на целевой сервер
        // send(server_fd, buffer, split_pos, 0);
        // usleep(5000);
        // send(server_fd, buffer + split_pos, bytes - split_pos, 0);
    }

    close(client_fd);
}

void* start_proxy_server(void* arg) {
    int server_fd, new_socket;
    struct sockaddr_in address;
    int opt = 1;
    int addrlen = sizeof(address);

    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        LOGI("Ошибка создания сокета");
        return NULL;
    }

    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT);

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        LOGI("Ошибка bind на порту %d", PORT);
        return NULL;
    }

    if (listen(server_fd, 10) < 0) {
        LOGI("Ошибка listen");
        return NULL;
    }

    LOGI("C-Ядро успешно запущено на 127.0.0.1:%d", PORT);

    while (1) {
        if ((new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen)) >= 0) {
            handle_client(new_socket);
        }
    }
    return NULL;
}

// Вызов из Kotlin через JNI
#include <jni.h>

JNIEXPORT void JNICALL
Java_com_ws_byedpi_MyDpiVpnService_startNativeCore(JNIEnv *env, jobject thiz) {
    pthread_t thread;
    pthread_create(&thread, NULL, start_proxy_server, NULL);
}
