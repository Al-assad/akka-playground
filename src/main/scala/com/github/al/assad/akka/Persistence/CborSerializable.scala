package com.github.al.assad.akka.Persistence

/**
 * Marker trait to tell Akka to serialize messages into CBOR using Jackson for sending over the network
 * See application.conf where it is bound to a serializer.
 */
trait CborSerializable
