/*
 * Copyright (C) 2018-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package com.lightbend.lagom.internal.persistence.couchbase

import javax.inject.Inject
import akka.actor.ActorSystem

/**
 * Internal API
 */
private[lagom] class CouchbaseReadSideSettings @Inject() (system: ActorSystem) {}
