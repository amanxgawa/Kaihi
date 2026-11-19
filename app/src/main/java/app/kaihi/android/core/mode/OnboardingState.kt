package app.kaihi.android.core.mode

/** Whether the user has finished first-run setup, by the wizard or by importing a server. Reads touch disk. */
interface OnboardingState {
    fun isCompleted(): Boolean
    fun markCompleted()
}
