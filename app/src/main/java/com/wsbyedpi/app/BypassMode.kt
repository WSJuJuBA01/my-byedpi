package com.wsbyedpi.app

enum class BypassMode(
    val title: String,
    val description: String,
    val args: String
) {
    MODE_1(
        title = "Обычный",
        description = "Базовый сплит и SNI-маскировка под wb.ru",
        args = "-f350 -Qr -f-1+sh -t6 -Qr -a4 -m3 -n wb.ru"
    ),
    MODE_2(
        title = "Disorder",
        description = "Агрессивная фрагментация, TTL и UDP-fake",
        args = "--disorder 1 --split 2+s --tlsrec 2+s --auto=torst,ssl_err --timeout 3 --disorder 1 --disorder 3+s --split 6+s --disorder 9+s --split 12+s --disorder 15+s --split 20+s --disorder 25+s --split 30+s --disorder 35+s --tlsrec 1+s --fake -40 --ttl 8 --md5sig --udp-fake 3"
    ),
    MODE_3(
        title = "Медиа & Discord",
        description = "Максимальный пробой UDP/QUIC и подмена SNI googlevideo",
        args = "-f-1+sh -r -e 1+s -q -t5 -a4 -m3 --udp-fake 3 -n googlevideo.com"
    );

    companion object {
        fun fromName(name: String?): BypassMode {
            return entries.find { it.name == name } ?: MODE_1
        }
    }
}
