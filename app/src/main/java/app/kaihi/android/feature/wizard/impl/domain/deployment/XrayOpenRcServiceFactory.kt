package app.kaihi.android.feature.wizard.impl.domain.deployment


internal object XrayOpenRcServiceFactory {
    fun create(): String = """
        #!/sbin/openrc-run

        name="kaihi-xray"
        description="Kaihi Xray service"
        command="/usr/local/bin/xray"
        command_args="run -config /usr/local/etc/xray/config.json"
        command_user="kaihi-xray:kaihi-xray"
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
            checkpath --file --owner kaihi-xray:kaihi-xray --mode 0640 "/var/log/${'$'}{RC_SVCNAME}.log"
        }
    """.trimIndent()
}
