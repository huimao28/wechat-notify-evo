package com.huimao.wechatnotifyevo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class HistoryRepositoryTest {
    @Test fun dedupeUsesInjectedHmacAndIgnoresDuplicate() = runBlocking {
        val dao = FakeHistoryDao()
        val crypto = FakeHistoryCrypto()
        val repository = HistoryRepository(
            dao = dao,
            settingsSource = HistorySettingsSource { HistorySettingsSnapshot(enabled = true) },
            crypto = crypto,
            ioDispatcher = Dispatchers.Unconfined,
            now = { NOW },
        )
        val message = sample()

        assertTrue(repository.record(message))
        assertFalse(repository.record(message))
        assertEquals(1, dao.rows.size)
        assertEquals(2, crypto.hmacInputs.size)
        assertEquals(crypto.hmacInputs[0], crypto.hmacInputs[1])
        assertFalse(dao.rows.single().text.ciphertext.contentEquals(message.text.toByteArray()))
    }

    @Test fun disabledAndExcludedMessagesNeverReachCryptoOrDatabase() = runBlocking {
        val crypto = FakeHistoryCrypto()
        val dao = FakeHistoryDao()
        var settings = HistorySettingsSnapshot()
        val repository = HistoryRepository(
            dao = dao,
            settingsSource = HistorySettingsSource { settings },
            crypto = crypto,
            ioDispatcher = Dispatchers.Unconfined,
        )

        assertFalse(repository.record(sample()))
        settings = HistorySettingsSnapshot(
            enabled = true,
            excludedStableConversationIds = setOf("stable-a"),
        )
        assertFalse(repository.record(sample()))
        assertTrue(crypto.hmacInputs.isEmpty())
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun distinctPayloadGetsDistinctDedupeTokenAndDecryptsViaFake() = runBlocking {
        val dao = FakeHistoryDao()
        val repository = HistoryRepository(
            dao = dao,
            settingsSource = HistorySettingsSource { HistorySettingsSnapshot(enabled = true) },
            crypto = FakeHistoryCrypto(),
            ioDispatcher = Dispatchers.Unconfined,
            now = { NOW },
        )

        assertTrue(repository.record(sample()))
        assertTrue(repository.record(sample().copy(text = "second")))
        val restored = repository.messages(0, 10)
        assertEquals(listOf("hello", "second"), restored.map { it.text })
        assertEquals(2, dao.rows.map { it.dedupeDigest }.distinct().size)
    }

    private fun sample() = HistoryMessage(
        conversationStableId = "stable-a",
        title = "title",
        sender = "sender",
        text = "hello",
        timestamp = NOW,
        type = "DIRECT",
        incoming = true,
        source = "test",
    )

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}

private class FakeHistoryCrypto : HistoryCrypto {
    val hmacInputs = mutableListOf<List<String>>()

    override fun encrypt(plaintext: String): EncryptedField = EncryptedField(
        ciphertext = ("encrypted:$plaintext").toByteArray(StandardCharsets.UTF_8),
        nonce = byteArrayOf(1),
    )

    override fun decrypt(field: EncryptedField): String =
        String(field.ciphertext, StandardCharsets.UTF_8).removePrefix("encrypted:")

    override fun dedupeHmac(parts: List<String>): String {
        hmacInputs += parts.toList()
        return parts.joinToString(separator = "\u0000", prefix = "fake-hmac:")
    }
}

private class FakeHistoryDao : HistoryDao {
    val rows = mutableListOf<CapturedMessageEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: CapturedMessageEntity): Long {
        if (rows.any { it.dedupeDigest == entity.dedupeDigest }) return -1L
        val id = nextId++
        rows += entity.copy(id = id)
        return id
    }

    override suspend fun messages(from: Long, limit: Int, offset: Int) = rows
        .filter { it.timestamp >= from }
        .sortedBy { it.timestamp }
        .drop(offset)
        .take(limit)

    override suspend fun conversationMessages(id: String, from: Long, limit: Int, offset: Int) =
        messages(from, Int.MAX_VALUE, 0)
            .filter { it.conversationStableId == id }
            .drop(offset)
            .take(limit)

    override suspend fun count(): Int = rows.size

    override suspend fun conversationSummaries(from: Long, limit: Int, offset: Int): List<ConversationSummaryEntity> =
        rows.filter { it.timestamp >= from }.groupBy { it.conversationStableId }
            .map { (id, messages) -> ConversationSummaryEntity(id, messages.maxOf { it.timestamp }, messages.size) }
            .sortedByDescending { it.lastTimestamp }.drop(offset).take(limit)

    override suspend fun latestConversationMessage(id: String): CapturedMessageEntity? =
        rows.filter { it.conversationStableId == id }.maxByOrNull { it.timestamp }

    override suspend fun encryptedBytes(): Long = rows.sumOf {
        (it.title.ciphertext.size + it.sender.ciphertext.size + it.text.ciphertext.size +
            it.title.nonce.size + it.sender.nonce.size + it.text.nonce.size).toLong()
    }

    override suspend fun deleteOlderThan(cutoff: Long): Int =
        removeWhere { it.timestamp < cutoff }

    override suspend fun deleteConversation(id: String): Int =
        removeWhere { it.conversationStableId == id }

    override suspend fun clear() = rows.clear()

    override suspend fun deleteOldest(count: Int): Int {
        val ids = rows.sortedWith(compareBy(CapturedMessageEntity::timestamp, CapturedMessageEntity::id))
            .take(count)
            .mapTo(hashSetOf()) { it.id }
        return removeWhere { it.id in ids }
    }

    private fun removeWhere(predicate: (CapturedMessageEntity) -> Boolean): Int {
        val before = rows.size
        rows.removeAll(predicate)
        return before - rows.size
    }
}
