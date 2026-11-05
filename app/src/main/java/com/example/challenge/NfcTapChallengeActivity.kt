package com.example.challenge

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.ChallengeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Place + proof: touch your own NFC tag (a cheap sticker kept in another room, for example the
 * bathroom) with the back of the phone. Needs a phone with NFC switched on.
 */
class NfcTapChallengeActivity : BaseChallengeActivity() {

    private val statusState = MutableStateFlow("Touch your NFC tag with the back of the phone")
    private var adapter: NfcAdapter? = null
    private var expectedTag: String? = null
    private var ready = false
    private var finished = false

    override fun getChallengeType(): ChallengeType = ChallengeType.NFC_TAP

    override fun getTotalStages(): Int = 1

    override fun getStageTimeLimit(stage: Int): Int = 150

    override fun onStageStarted(stage: Int) {
        finished = false
    }

    override fun onResetToStage1() {
        finished = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        val nfc = adapter
        if (nfc == null || !nfc.isEnabled) {
            fallbackTo(QrChallengeActivity::class.java, "NFC is off or missing. Switching to QR.")
            return
        }
        if (alarmId > 0) {
            lifecycleScope.launch {
                val tag = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext).alarmDao().getAlarmById(alarmId)?.nfcTag
                }
                expectedTag = tag?.takeIf { it.isNotBlank() }
                ready = true
            }
        } else {
            ready = true // test mode: any tag works
        }
    }

    override fun onResume() {
        super.onResume()
        adapter?.enableReaderMode(
            this,
            NfcAdapter.ReaderCallback { tag ->
                val id = tag.id.joinToString("") { "%02X".format(it) }
                runOnUiThread { onTag(id) }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    private fun onTag(id: String) {
        if (!ready || finished || isCompletedState.value) return
        val expected = expectedTag
        if (expected == null || id.equals(expected, ignoreCase = true)) {
            finished = true
            statusState.value = "Tag found!"
            setFeedbackMessage("NFC tag found! You are up.", isSuccess = true)
            completeCurrentStage()
        } else {
            setFeedbackMessage("This is not your tag. Find the right one.", isError = true)
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val status by statusState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            TaskInfoCard(
                title = "Touch your NFC tag",
                subtitle = "Get up, go to your tag (for example in the bathroom) and touch it with the back of the phone."
            )
            Text(
                text = "\uD83D\uDCF6",
                fontSize = 72.sp
            )
            Text(
                text = status,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
