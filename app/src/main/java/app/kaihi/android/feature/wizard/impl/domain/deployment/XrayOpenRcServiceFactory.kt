package app.kaihi.android.feature.wizard.impl.domain.deployment


internal object XrayOpenRcServiceFactory {
    fun create(): String = """
        #!/sbin/openrc-run

        command="/usr/local/bin/xray"
        command_args="run -config /usr/local/etc/xray/config.json"
        command_user="xray:xray"
        capabilities="^cap_net_bind_service"
        no_new_privs="yes"
        command_background="yes"
        pidfile="/run/${'$'}{RC_SVCNAME}.pid"
        output_log="/var/log/${'$'}{RC_SVCNAME}.log"
        error_log="/var/log/${'$'}{RC_SVCNAME}.log"

        depend() {
            need net
            after firewall
        }

        start_pre() {
            checkpath --file --owner xray:xray --mode 0640 "/var/log/${'$'}{RC_SVCNAME}.log"
        }
    """.trimIndent()
}
