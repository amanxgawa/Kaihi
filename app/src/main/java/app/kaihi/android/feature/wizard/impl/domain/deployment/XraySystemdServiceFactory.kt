package app.kaihi.android.feature.wizard.impl.domain.deployment

internal object XraySystemdServiceFactory {
    fun create(): String = """
        [Unit]
        Description=Kaihi Xray service
        After=network-online.target
        Wants=network-online.target

        [Service]
        Type=simple
        User=kaihi-xray
        Group=kaihi-xray
        ExecStart=/usr/local/bin/xray run -config /usr/local/etc/xray/config.json
        Restart=on-failure
        RestartSec=3
        CapabilityBoundingSet=CAP_NET_BIND_SERVICE
        AmbientCapabilities=CAP_NET_BIND_SERVICE
        NoNewPrivileges=true
        UMask=0077
        LimitNOFILE=1000000
        ProtectSystem=strict
        ProtectHome=true
        PrivateTmp=true
        PrivateDevices=true
        ProtectKernelTunables=true
        ProtectKernelModules=true
        ProtectControlGroups=true
        RestrictSUIDSGID=true
        LockPersonality=true

        [Install]
        WantedBy=multi-user.target
    """.trimIndent()
}
