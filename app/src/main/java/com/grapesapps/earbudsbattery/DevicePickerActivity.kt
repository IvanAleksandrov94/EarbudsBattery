package com.grapesapps.earbudsbattery

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider

/** Full-screen configuration opened by Smartspacer, without a launcher entry. */
class DevicePickerActivity : Activity() {
    private lateinit var targetId: String
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var permissionPanel: LinearLayout
    private val permissionPreferences by lazy { getSharedPreferences("permission", MODE_PRIVATE) }

    private fun hasBluetoothPermission() =
        checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private val selected = linkedSetOf<String>()
    private var connected = emptyList<EarbudsBattery>()
    private var generation = 0
    private val store by lazy { BatteryStore(this) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: CharSequence, size: Float = 16f) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(230, 230, 230))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetId =
            intent.getStringExtra("smartspacer_id")
                ?: run {
                    finish()
                    return
                }
        store.remember(store.load(targetId))
        selected.addAll(
            savedInstanceState?.getStringArrayList("selected")
                ?: store.selections(targetId).orEmpty()
        )
        setContentView(createContent())
        if (
            !hasBluetoothPermission() &&
                !permissionPreferences.getBoolean("asked", false) &&
                savedInstanceState == null
        )
            requestBluetoothPermission()
        render()
        refresh()
    }

    private fun createContent(): LinearLayout {
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(28, 28, 28))
                setOnApplyWindowInsetsListener { view, insets ->
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    insets
                }
            }
        root.addView(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    ImageButton(this@DevicePickerActivity).apply {
                        setImageResource(R.drawable.ic_back)
                        setColorFilter(Color.LTGRAY)
                        contentDescription = getString(android.R.string.cancel)
                        setBackgroundColor(Color.TRANSPARENT)
                        setOnClickListener { finish() }
                    },
                    LinearLayout.LayoutParams(dp(64), dp(56)),
                )
            }
        )
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(20), dp(20), dp(16))
            }
        content.addView(
            label(getString(R.string.target_label), 32f).apply {
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(24))
            }
        )
        permissionPanel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(getString(R.string.bluetooth_permission_hint), 14f))
                addView(
                    Button(this@DevicePickerActivity).apply {
                        setText(R.string.bluetooth_permission_button)
                        setOnClickListener { requestBluetoothPermission() }
                    }
                )
            }
        content.addView(permissionPanel)
        content.addView(
            label(getString(R.string.saved_devices), 14f).apply { setPadding(0, dp(12), 0, dp(12)) }
        )
        status = label("").apply { setPadding(0, 0, 0, dp(12)) }
        content.addView(status)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        root.addView(
            ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        root.addView(
            LinearLayout(this).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(
                    Button(this@DevicePickerActivity).apply {
                        setText(R.string.refresh_devices)
                        setOnClickListener { refresh() }
                    },
                    LinearLayout.LayoutParams(0, -2, 1f),
                )
                addView(
                    Button(this@DevicePickerActivity).apply {
                        setText(R.string.save_devices)
                        setOnClickListener {
                            SystemBatterySource.forget(targetId)
                            store.selectMany(targetId, selected, connected)
                            SmartspacerTargetProvider.notifyChange(
                                this@DevicePickerActivity,
                                EarbudsTarget::class.java,
                                targetId,
                            )
                            setResult(RESULT_OK)
                            finish()
                        }
                    },
                    LinearLayout.LayoutParams(0, -2, 1f),
                )
            }
        )
        return root
    }

    private fun requestBluetoothPermission() {
        if (hasBluetoothPermission()) return
        if (
            permissionPreferences.getBoolean("asked", false) &&
                !shouldShowRequestPermissionRationale(android.Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName"),
                )
            )
        } else requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 1)
    }

    override fun onResume() {
        super.onResume()
        if (::permissionPanel.isInitialized)
            permissionPanel.visibility =
                if (hasBluetoothPermission()) android.view.View.GONE else android.view.View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1) return
        permissionPreferences.edit().putBoolean("asked", true).apply()
        permissionPanel.visibility =
            if (hasBluetoothPermission()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun refresh() {
        val token = ++generation
        status.visibility = android.view.View.VISIBLE
        status.setText(R.string.loading_devices)
        SystemBatterySource.fetch(this) { rows ->
            if (isFinishing || isDestroyed || token != generation) return@fetch
            connected = BatteryParser.parseDevices(rows.orEmpty())
            store.remember(connected)
            status.text =
                when {
                    rows == null -> getString(R.string.device_source_error)
                    store.knownDevices().isEmpty() -> getString(R.string.no_devices)
                    else -> ""
                }
            status.visibility =
                if (status.text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            render()
        }
    }

    private fun render() {
        list.removeAllViews()
        val devices = store.knownDevices().sortedBy { it.title }
        val duplicates = devices.groupingBy { it.title }.eachCount()
        val connectedAddresses = connected.mapTo(hashSetOf()) { it.address }
        for (device in devices) {
            list.addView(
                createDeviceRow(
                    device,
                    device.address in connectedAddresses,
                    duplicates.getValue(device.title) > 1,
                )
            )
        }
    }

    private fun createDeviceRow(
        device: EarbudsBattery,
        isConnected: Boolean,
        showAddress: Boolean,
    ): LinearLayout {
        val row =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(80)
                setPadding(dp(4), dp(12), 0, dp(12))
            }
        row.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_earbuds)
                setColorFilter(Color.LTGRAY)
            },
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(20) },
        )
        row.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(device.title, 16f).apply { setTypeface(null, Typeface.NORMAL) })
                addView(
                    label(EarbudsTarget.batteryLine(this@DevicePickerActivity, device), 14f).apply {
                        setPadding(0, dp(4), 0, 0)
                    }
                )
                if (!isConnected)
                    addView(
                        label(getString(R.string.disconnected_device), 12f).apply {
                            setTextColor(Color.GRAY)
                        }
                    )
                if (showAddress) addView(label(device.address, 12f))
            },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        val checkbox =
            CheckBox(this).apply {
                isChecked = device.address in selected
                contentDescription = device.title
                tag = device.address
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(device.address) else selected.remove(device.address)
                }
            }
        row.addView(checkbox)
        row.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
        return row
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("selected", ArrayList(selected))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        generation++
        super.onDestroy()
    }
}
