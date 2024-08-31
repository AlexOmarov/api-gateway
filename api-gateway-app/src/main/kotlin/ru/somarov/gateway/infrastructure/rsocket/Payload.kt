package ru.somarov.gateway.infrastructure.rsocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil.isText
import io.netty.buffer.Unpooled
import io.rsocket.Payload
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.internal.BufferPool
import io.rsocket.kotlin.metadata.CompositeMetadata.Reader.read
import io.rsocket.metadata.CompositeMetadata
import io.rsocket.metadata.CompositeMetadataCodec
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.util.DefaultPayload
import kotlinx.serialization.SerializationException
import ru.somarov.gateway.infrastructure.rsocket.server.Message
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.reflect.KClass
import io.rsocket.kotlin.payload.Payload as payload

@ExperimentalMetadataApi
fun KClass<Payload>.from(input: io.rsocket.kotlin.payload.Payload): Payload {
    val metadata = ByteBufAllocator.DEFAULT.compositeBuffer()
    input.metadata?.copy()?.read(BufferPool.Default)?.entries?.forEach {
        CompositeMetadataCodec.encodeAndAddMetadata(
            /* compositeMetaData = */ metadata,
            /* allocator = */ ByteBufAllocator.DEFAULT,
            /* customMimeType = */ it.mimeType.toString(),
            /* metadata = */ Unpooled.wrappedBuffer(it.content.readBytes())
        )
    }

    return DefaultPayload.create(Unpooled.wrappedBuffer(input.data.readBytes()), metadata)
}

fun <T : Any> Payload.deserialize(mapper: ObjectMapper, log: org.slf4j.Logger, clazz: Class<T>): Message<T> {
    val data: T? = try {
        val array = getByteArray(this.data)
        if (array.isNotEmpty()) {
            mapper.readValue(getByteArray(this.data), clazz)
        } else {
            null
        }
    } catch (e: SerializationException) {
        log.error("Got error while deserializing cbor to string", e)
        null
    }
    var routing = "null"
    val encoding = Charset.forName("UTF8")
    val metadata = CompositeMetadata(Unpooled.wrappedBuffer(this.metadata), false)
        .map {
            val content = if (isText(it.content, encoding)) it.content.toString(encoding) else "Not text"
            if (it.mimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string) {
                routing = if (content.isNotEmpty()) content.substring(1) else content
                it.mimeType to routing
            } else {
                it.mimeType to content
            }
        }.toMap()

    return Message(data, metadata, routing)
}

suspend fun Payload.toKotlinPayload(): io.rsocket.kotlin.payload.Payload {
    return payload(
        ByteReadChannel(this.data).readRemaining(),
        ByteReadChannel(this.metadata).readRemaining()
    )
}

@Suppress("kotlin:S6518") // Here byteArray should be filled with get method
private fun getByteArray(buffer: ByteBuffer): ByteArray {
    val byteArray = ByteArray(buffer.remaining())
    buffer.get(byteArray)
    buffer.rewind()
    return byteArray
}