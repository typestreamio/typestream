package io.typestream.scheduler

import io.typestream.k8s.K8sClient

class K8sJob(override val id: String) : Job {
    override fun remove() {
        K8sClient().deleteJob(id)
    }

    override fun start() {
        //K8sClient().startJob(id)  maybe. Only works if the job is already there
    }

    override fun output() = K8sClient().jobOutput(id)

    override fun stop() {
        //K8sClient().stopJob(id)  maybe?
    }

    override fun state(): Job.State {
        //TODO get from k8s
        return Job.State.RUNNING
    }
}
