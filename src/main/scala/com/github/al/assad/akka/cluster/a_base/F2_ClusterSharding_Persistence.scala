package com.github.al.assad.akka.cluster.a_base

/**
 * Akka cluster sharding persistence sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html#persistence-example
 *
 * On the [[ClusterShardingTest3]] since the default region is not persistent,
 * the region data is directly lost when the node it is on goes offline.
 * This can be prevented if the region is persisted, but this comes at the cost
 * of performance.
 */
object ClusterShardingPersistenceTest {


}


trait ClusterShardingPersistenceTest {

}
