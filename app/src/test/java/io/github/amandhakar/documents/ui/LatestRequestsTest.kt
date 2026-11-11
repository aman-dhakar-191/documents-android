package io.github.amandhakar.documents.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestRequestsTest {
    @Test fun onlyTheNewestWaitingRequestIsHandled() = runBlocking {
        val requests = LatestRequests<Int>()
        requests.submit(1)
        requests.submit(2)
        requests.submit(3)
        requests.close()
        val seen = mutableListOf<Int>()
        requests.process { seen += it }
        assertEquals(listOf(3), seen)
    }

    @Test fun requestsThatArriveWhileOneIsBeingHandledAreReducedToTheNewest() = runBlocking {
        val requests = LatestRequests<Int>()
        val seen = mutableListOf<Int>()
        requests.submit(1)
        requests.process {
            if (it == 1) {
                // While request 1 is being handled, three more come in.
                requests.submit(2)
                requests.submit(3)
                requests.submit(4)
            }
            seen += it
            if (it == 4) requests.close()
        }
        assertEquals(listOf(1, 4), seen)
    }

    @Test fun everyRequestIsHandledWhenTheyDoNotOverlap() = runBlocking {
        val requests = LatestRequests<Int>()
        val seen = mutableListOf<Int>()
        requests.submit(1)
        requests.process {
            seen += it
            if (it < 3) requests.submit(it + 1) else requests.close()
        }
        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test fun closingWithNothingWaitingEndsTheLoop() = runBlocking {
        val requests = LatestRequests<Int>()
        requests.close()
        val seen = mutableListOf<Int>()
        requests.process { seen += it }
        assertEquals(emptyList<Int>(), seen)
    }
}
