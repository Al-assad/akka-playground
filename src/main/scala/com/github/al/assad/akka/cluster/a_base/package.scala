package com.github.al.assad.akka.cluster

import akka.cluster.MemberStatus
import akka.cluster.typed.Cluster
import com.github.al.assad.akka.assertUnit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

package object a_base {

  // wait unit cluster to specified states
  def waitClusterUnit(state: MemberStatus, clusters: Cluster*): Unit =
    Await.result(Future.sequence(clusters map { cluster => assertUnit(cluster.selfMember.status == state) }), 45.seconds)

  // wait unit cluster up
  def waitClusterUp(clusters: Cluster*): Unit = waitClusterUnit(MemberStatus.Up, clusters: _*)

  // println all membership
  def printMembers(cluster: Cluster): Unit = println(
    s"""@Message Observation from ${cluster.selfMember.address}
       |${cluster.state.members.map("\t" + _.toString()).mkString("\n")}""".stripMargin)


}
