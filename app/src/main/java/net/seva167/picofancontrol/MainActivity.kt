package net.seva167.picofancontrol

import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import net.seva167.picofancontrol.ui.theme.PicoFanControlTheme
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

const val SPEED_FNAME = "tgtSpeed"

class MainActivity : ComponentActivity() {
    val fanRpmUpdHandler = Handler()
    val fanRpm = mutableIntStateOf(0)
    val fanRpmUpdRunnable = object : Runnable {
        override fun run() {
            val out = runAsSU("gd32ipdclient_test getfanrpm")
            if (out != null)
                fanRpm.intValue = out.split(' ')[3].toInt()
            fanRpmUpdHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val speedFile = File(filesDir, SPEED_FNAME)
        if (!speedFile.exists()) {
            speedFile.writeText("50")
        }

        setContent {
            PicoFanControlTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    FanControl(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        fanRpm = fanRpm,
                        onExit = {
                            finish()
                            exitProcess(0)
                        },
                        filesDir
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fanRpmUpdHandler.postDelayed(fanRpmUpdRunnable, 1000)
    }

    override fun onPause() {
        super.onPause()
        fanRpmUpdHandler.removeCallbacks(fanRpmUpdRunnable)
    }

    override fun onStop() {
        super.onStop()
        fanRpmUpdHandler.removeCallbacks(fanRpmUpdRunnable)
    }
}

fun runAsSU(cmd: String): String? {
    var process: Process? = null
    try {
        process = Runtime.getRuntime().exec("su -c $cmd")
        process.waitFor()
        val output = BufferedReader(InputStreamReader(process.inputStream)).readLine()
        return output
    }
    catch (e: Exception) {
        Log.e("SUCmd", e.toString())
        return null
    }
    finally {
        process?.destroy()
    }
}

fun runGD32Cmd(subCmd: String, arg: Int): Boolean {
    val output = runAsSU("gd32ipdclient_test $subCmd $arg")
    if (output != null) {
        Log.d("FanCmd", output)
        return output.split(' ')[2] == "success"
    }
    else
        return false
}

fun setFanTestSpeed(speed: Int) {
    val result = runGD32Cmd("setfantestspeed", speed)

    if (result)
        Log.i("FanCmd", "Set fan to $speed%")
    else
        Log.e("FanCmd", "Failed to set fan speed!")
}

fun setFanTestMode(enable: Boolean) {
    val result = runGD32Cmd("setfantestmode", if (enable) 1 else 0)

    if (result)
        Log.i("FanCmd", "Set fan test mode to $enable")
    else
        Log.e("FanCmd", "Failed to set fan mode!")
}

@Composable
fun FanControl(modifier: Modifier = Modifier, fanRpm: MutableIntState, onExit: () -> Unit, filesDir: File) {
    var overrideEnabled by remember { mutableStateOf(false) }
    var targetSpeed by remember { mutableFloatStateOf(50f) }

    LaunchedEffect(Unit) {
        val speedFile = File(filesDir, SPEED_FNAME)
        targetSpeed = speedFile.readText().toFloat()

        // Check if in fan test mode
        if (runGD32Cmd("setfantestspeed", 69)) {
            setFanTestSpeed(targetSpeed.fastRoundToInt())
            overrideEnabled = true
        } else
            overrideEnabled = false
    }

    FanControlContent(
        overrideEnabled = overrideEnabled,
        targetSpeed = targetSpeed,
        fanRpm = fanRpm,
        onFanOverrideChange = {
            overrideEnabled = it
            setFanTestMode(overrideEnabled)
            if (overrideEnabled)
                setFanTestSpeed(targetSpeed.fastRoundToInt())
        },
        onTargetSpeedSliderChange = {
            targetSpeed = it
            val speedFile = File(filesDir, SPEED_FNAME)
            speedFile.writeText(targetSpeed.fastRoundToInt().toString())
            if (overrideEnabled)
                setFanTestSpeed(targetSpeed.fastRoundToInt())
        },
        onExitClick = onExit,
        modifier = modifier
    )
}

@Composable
fun FanControlContent(
    overrideEnabled: Boolean,
    targetSpeed: Float,
    fanRpm: MutableIntState,
    onFanOverrideChange: (Boolean) -> Unit,
    onTargetSpeedSliderChange: (Float) -> Unit,
    onExitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = colorResource(R.color.main_bg),
        shape = RoundedCornerShape(32.dp)
    ) {
        Column (
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Pico fan control",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 32.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Column {
                val enImg = painterResource(R.drawable.chilly)
                val diImg = painterResource(R.drawable.notfan)

                Image(
                    painter = if (overrideEnabled) enImg else diImg,
                    contentDescription = "Active",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .width(200.dp)
                        .height(174.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Text(
                    if (overrideEnabled) "Fan override is enabled" else "Fan override is disabled",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            Column (
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Row (
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                   Text("Enable fan override")
                    Switch(overrideEnabled, onCheckedChange = onFanOverrideChange)
                }

                Row (
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Fan speed: ${targetSpeed.fastRoundToInt()}%")
                    Slider(
                        value = targetSpeed,
                        valueRange = 50f..100f,
                        onValueChange = onTargetSpeedSliderChange
                    )
                }
            }

            Text(
                "Current fan RPM: ${fanRpm.intValue}",
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Button(
                onClick = onExitClick,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.dropdown_bg),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp)
            ) {
                Text("Close")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable
fun FanControlPreview() {
    PicoFanControlTheme {
        FanControlContent(
            overrideEnabled = false,
            targetSpeed = 50f,
            fanRpm = remember { mutableIntStateOf(10000) },
            onFanOverrideChange = {},
            onTargetSpeedSliderChange = {},
            onExitClick = {}
        )
    }
}