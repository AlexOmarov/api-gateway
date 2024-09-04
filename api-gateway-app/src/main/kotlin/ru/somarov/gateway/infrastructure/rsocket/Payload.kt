package ru.somarov.gateway.infrastructure.rsocket

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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import java.nio.charset.Charset
import java.util.*
import io.rsocket.kotlin.payload.Payload as payload


@ExperimentalMetadataApi
fun io.rsocket.kotlin.payload.Payload.toJavaPayload(): Payload {
    val metadata = ByteBufAllocator.DEFAULT.compositeBuffer()
    this.metadata?.copy()?.read(BufferPool.Default)?.entries?.forEach {
        CompositeMetadataCodec.encodeAndAddMetadata(
            /* compositeMetaData = */ metadata,
            /* allocator = */ ByteBufAllocator.DEFAULT,
            /* customMimeType = */ it.mimeType.toString(),
            /* metadata = */ Unpooled.wrappedBuffer(it.content.readBytes())
        )
    }

    return DefaultPayload.create(Unpooled.wrappedBuffer(data.readBytes()), metadata)
}

suspend fun Payload.toKotlinPayload(): io.rsocket.kotlin.payload.Payload {
    return payload(
        ByteReadChannel(this.data).readRemaining(),
        ByteReadChannel(this.metadata).readRemaining()
    )
}


@OptIn(ExperimentalSerializationApi::class)
@Suppress("kotlin:S6518") // Here byteArray should be filled with get method
inline fun <reified T : Any> Payload.deserialize(): Message<T> {
    val encoding = Charset.forName("UTF8")
    val metadata = CompositeMetadata(Unpooled.wrappedBuffer(this.metadata), false)
        .associate {
            (it.mimeType ?: "Unknown mime type ${UUID.randomUUID()}") to
                    if (isText(it.content, encoding)) it.content.toString(encoding) else "Not text"
        }

    val mime = metadata[WellKnownMimeType.MESSAGE_RSOCKET_MIMETYPE.string]

    val array = ByteArray(data.remaining())
    data.get(array)
    data.rewind()

    val body = if (array.isNotEmpty()) {
        when (mime) {
            WellKnownMimeType.APPLICATION_CBOR.name -> Cbor.Default.decodeFromByteArray<T>(array)
            else -> Json.Default.decodeFromString(String(array))
        }
    } else {
        null
    }

    return Message(body, metadata)
}

data class Message<T: Any>(
    val body: T?,
    val metadata: Map<String, String>
)

