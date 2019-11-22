/*
 * Copyright (C) 2018-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.persistence.couchbase.internal

import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.annotation.InternalApi

import scala.annotation.tailrec
import scala.util.Random

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object UUIDGenerator {
  def extractMac(): Long = {
    // use first interface we find
    def firstAvailableMac(enumeration: java.util.Enumeration[NetworkInterface]): Array[Byte] =
      if (enumeration.hasMoreElements) {
        val mac = enumeration.nextElement().getHardwareAddress
        if (mac ne null) mac
        else firstAvailableMac(enumeration)
      } else {
        Array.emptyByteArray
      }
    val interfaces = NetworkInterface.getNetworkInterfaces()
    val mac = firstAvailableMac(interfaces)
    if (mac.isEmpty)
      // spec says to generate pseudo random if there is no mac but we don't need to care about that case
      // because if we can connect to a db there is an interface
      throw new RuntimeException("Could not find any network interfaces to base UUIDs on")
    else
      macAsLong(mac)
  }

  def macAsLong(addressArray: Array[Byte]): Long = {
    var address = 0xFFL & addressArray(5)
    address |= (0xFFL & addressArray(4)) << 8
    address |= (0xFFL & addressArray(3)) << 16
    address |= (0xFFL & addressArray(2)) << 24
    address |= (0xFFL & addressArray(1)) << 32
    address |= (0xFFL & addressArray(0)) << 40
    address
  }

  def apply(): UUIDGenerator = {
    val node = extractMac()

    // Spec: If the previous value of the clock sequence is known, it
    // can just be incremented; otherwise it should be set to a random or
    // high-quality pseudo-random value.
    val clockSeqAndNode: Long = TimeBasedUUIDs.lsbFromNode(node, Random.nextInt())
    new UUIDGenerator(clockSeqAndNode)
  }
}

/**
 * INTERNAL API
 *
 * Generates time-based UUIDs (also known as Version 1 UUIDs)
 *
 * Inspired by the Cassandra Java client time-based UUIDs `com.datastax.driver.core.utils.UUIDs`
 *
 * @see https://www.ietf.org/rfc/rfc4122.txt
 *
 */
@InternalApi
private[akka] final class UUIDGenerator(clockSeqAndNode: Long) {
  // makes sure we don't return the same timestamp twice
  private val lastTimeStamp = new AtomicLong(0L)

  /*
   * Note that currently we use {@link System#currentTimeMillis} for a base time in
   * milliseconds, which is not monotonic and might have less than 1 ms resolution, on top of that
   * the precision we need is 100-nanosecond intervals we can only
   * generate at most 10K UUID if the resolution of currentTimeMillis is one millisecond safely.
   */
  @tailrec
  def currentTimestamp(): UUIDTimestamp = {
    val now = UUIDTimestamp.now()
    val last = UUIDTimestamp(lastTimeStamp.get)

    // simple case, time has passed since last uuid was generated, just pick the next and try to cas
    if (now > last) {
      if (lastTimeStamp.compareAndSet(last.nanoTimestamp, now.nanoTimestamp)) now
      else currentTimestamp() // lost cas, try again
    } else {
      val lastMs = last.toMs
      val candidate = last.next

      if (now.toMs < lastMs) {
        // The clock went back in time (currentTimeMillis is not monotonic) just pick next and ids will hopefully catch up
        // Since we don't have an actual clock but a random number for clock (which should increase to solve this according
        // to spec) we move forward with the time part instead
        if (lastTimeStamp.compareAndSet(last.nanoTimestamp, candidate.nanoTimestamp)) candidate
        else currentTimestamp() // lost the cas, try again
      } else if (candidate.toMs != lastMs) {
        // We trying to pick more than one ids in the same currentTimeMillis resolution interval
        // Generating more than 10k uuids in one millisecond is not likely with the Couchbase journal
        // but if that happens we just hot-spin and try again
        currentTimestamp()
      } else if (lastTimeStamp.compareAndSet(last.nanoTimestamp, candidate.nanoTimestamp)) {
        candidate
      } else currentTimestamp() // lost the cas, try again
    }
  }

  def nextUuid(): UUID = {
    val timestamp = currentTimestamp()
    TimeBasedUUIDs.create(timestamp, clockSeqAndNode)
  }
}
