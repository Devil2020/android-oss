package com.kickstarter.viewmodels

import android.util.Pair
import androidx.annotation.NonNull
import com.kickstarter.R
import com.kickstarter.libs.Either
import com.kickstarter.libs.Environment
import com.kickstarter.libs.FragmentViewModel
import com.kickstarter.libs.KSString
import com.kickstarter.libs.rx.transformers.Transformers.*
import com.kickstarter.libs.utils.*
import com.kickstarter.models.Backing
import com.kickstarter.models.Project
import com.kickstarter.models.Reward
import com.kickstarter.models.StoredCard
import com.kickstarter.ui.data.PledgeStatusData
import com.kickstarter.ui.fragments.BackingFragment
import com.stripe.android.model.Card
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import type.CreditCardPaymentType
import type.CreditCardTypes
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

interface BackingFragmentViewModel {
    interface Inputs {
        /** Call when the pledge has been successfully updated. */
        fun pledgeSuccessfullyUpdated()

        /** Configure with current project.  */
        fun project(project: Project)

        /** Call when the mark as received checkbox is checked. */
        fun receivedCheckboxToggled(checked: Boolean)

        /** Call when the swipe refresh layout is triggered. */
        fun refreshProject()
    }

    interface Outputs {
        /** Emits the backer's avatar URL. */
        fun backerAvatar(): Observable<String>

        /** Emits the backer's name. */
        fun backerName(): Observable<String>

        /** Emits the backer's sequence. */
        fun backerNumber(): Observable<String>

        /** Emits the expiration of the backing's card. */
        fun cardExpiration(): Observable<String>

        /** Emits the name of the card issuer from [Card.CardBrand] or Google Pay or Apple Pay string resources. */
        fun cardIssuer(): Observable<Either<String, Int>>

        /** Emits the last four digits of the backing's card. */
        fun cardLastFour(): Observable<String>

        /** Emits the card brand drawable to display. */
        fun cardLogo(): Observable<Int>

        /** Emits when we should notify the [BackingFragment.BackingDelegate] to refresh the project. */
        fun notifyDelegateToRefreshProject(): Observable<Void>

        /** Emits a boolean determining if the payment method section should be visible. */
        fun paymentMethodIsGone(): Observable<Boolean>

        /** Emits the amount pledged minus the shipping. */
        fun pledgeAmount(): Observable<CharSequence>

        /** Emits the date the backing was pledged on. */
        fun pledgeDate(): Observable<String>

        /** Emits the string resource ID that best represents the pledge status and associated data. */
        fun pledgeStatusData(): Observable<PledgeStatusData>

        /** Emits a boolean determining if the pledge summary should be visible. */
        fun pledgeSummaryIsGone(): Observable<Boolean>

        /** Emits the project and currently backed reward. */
        fun projectAndReward(): Observable<Pair<Project, Reward>>

        /** Emits a boolean that determines if received checkbox should be checked. */
        fun receivedCheckboxChecked(): Observable<Boolean>

        /** Emits a boolean determining if the delivered section should be visible. */
        fun receivedSectionIsGone(): Observable<Boolean>

        /** Emits the shipping amount of the backing. */
        fun shippingAmount(): Observable<CharSequence>

        /** Emits the shipping location of the backing. */
        fun shippingLocation(): Observable<String>

        /** Emits a boolean determining if the shipping summary should be visible. */
        fun shippingSummaryIsGone(): Observable<Boolean>

        /** Emits when the backing has successfully been updated. */
        fun showUpdatePledgeSuccess(): Observable<Void>

        /** Emits a boolean determining if the swipe refresher is visible. */
        fun swipeRefresherProgressIsVisible(): Observable<Boolean>

        /** Emits the total amount pledged. */
        fun totalAmount(): Observable<CharSequence>
    }

    class ViewModel(@NonNull val environment: Environment) : FragmentViewModel<BackingFragment>(environment), Inputs, Outputs {

        private val pledgeSuccessfullyCancelled = PublishSubject.create<Void>()
        private val projectInput = PublishSubject.create<Project>()
        private val receivedCheckboxToggled = PublishSubject.create<Boolean>()
        private val refreshProject = PublishSubject.create<Void>()

        private val backerAvatar = BehaviorSubject.create<String>()
        private val backerName = BehaviorSubject.create<String>()
        private val backerNumber = BehaviorSubject.create<String>()
        private val cardExpiration = BehaviorSubject.create<String>()
        private val cardIssuer = BehaviorSubject.create<Either<String, Int>>()
        private val cardLastFour = BehaviorSubject.create<String>()
        private val cardLogo = BehaviorSubject.create<Int>()
        private val notifyDelegateToRefreshProject = PublishSubject.create<Void>()
        private val paymentMethodIsGone = BehaviorSubject.create<Boolean>()
        private val pledgeAmount = BehaviorSubject.create<CharSequence>()
        private val pledgeDate = BehaviorSubject.create<String>()
        private val pledgeStatusData = BehaviorSubject.create<PledgeStatusData>()
        private val pledgeSummaryIsGone = BehaviorSubject.create<Boolean>()
        private val projectAndReward = BehaviorSubject.create<Pair<Project, Reward>>()
        private val receivedCheckboxChecked = BehaviorSubject.create<Boolean>()
        private val receivedSectionIsGone = BehaviorSubject.create<Boolean>()
        private val shippingAmount = BehaviorSubject.create<CharSequence>()
        private val shippingLocation = BehaviorSubject.create<String>()
        private val shippingSummaryIsGone = BehaviorSubject.create<Boolean>()
        private val showUpdatePledgeSuccess = PublishSubject.create<Void>()
        private val swipeRefresherProgressIsVisible = BehaviorSubject.create<Boolean>()
        private val totalAmount = BehaviorSubject.create<CharSequence>()

        private val apiClient = this.environment.apiClient()
        private val currentUser = this.environment.currentUser()
        private val ksCurrency = this.environment.ksCurrency()
        val ksString: KSString = this.environment.ksString()

        val inputs: Inputs = this
        val outputs: Outputs = this

        init {

            this.pledgeSuccessfullyCancelled
                    .compose(bindToLifecycle())
                    .subscribe(this.showUpdatePledgeSuccess)

            val backedProject = this.projectInput
                    .filter { it.isBacking }

            val backing = backedProject
                    .map { it.backing() }
                    .ofType(Backing::class.java)

            val backer = this.currentUser.loggedInUser()

            backer
                    .map { it.name() }
                    .compose(bindToLifecycle())
                    .subscribe(this.backerName)

            backer
                    .map { it.avatar().medium() }
                    .compose(bindToLifecycle())
                    .subscribe(this.backerAvatar)

            backedProject
                    .map { project -> project.rewards()?.firstOrNull { BackingUtils.isBacked(project, it) }?.let { Pair(project, it) } }
                    .compose(bindToLifecycle())
                    .subscribe(this.projectAndReward)

            backing
                    .map { NumberUtils.format(it.sequence().toFloat()) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.backerNumber)

            backing
                    .map { DateTimeUtils.longDate(it.pledgedAt()) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.pledgeDate)

            backing
                    .map { it.amount() - it.shippingAmount() }
                    .compose<Pair<Double, Project>>(combineLatestPair(backedProject))
                    .map { ProjectViewUtils.styleCurrency(it.first, it.second, this.ksCurrency) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.pledgeAmount)

            backing
                    .map { ObjectUtils.isNull(it.locationId()) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe {
                        this.pledgeSummaryIsGone.onNext(it)
                        this.shippingSummaryIsGone.onNext(it)
                    }

            backedProject
                    .map { pledgeStatusData(it) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.pledgeStatusData)

            backing
                    .map { it.shippingAmount() }
                    .compose<Pair<Float, Project>>(combineLatestPair(backedProject))
                    .map { ProjectViewUtils.styleCurrency(it.first.toDouble(), it.second, this.ksCurrency) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.shippingAmount)

            backing
                    .map { it.locationName()?.let { name -> name } }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.shippingLocation)

            backing
                    .map { it.amount() }
                    .compose<Pair<Double, Project>>(combineLatestPair(backedProject))
                    .map { ProjectViewUtils.styleCurrency(it.first, it.second, this.ksCurrency) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.totalAmount)

            backing
                    .map { it.paymentSource() }
                    .map { CreditCardPaymentType.safeValueOf(it?.paymentType()) }
                    .map { it == CreditCardPaymentType.ANDROID_PAY || it == CreditCardPaymentType.APPLE_PAY || it == CreditCardPaymentType.CREDIT_CARD }
                    .map { BooleanUtils.negate(it) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.paymentMethodIsGone)

            val paymentSource = backing
                    .map { it.paymentSource() }
                    .filter { it != null }
                    .ofType(Backing.PaymentSource::class.java)

            val simpleDateFormat = SimpleDateFormat(StoredCard.DATE_FORMAT, Locale.getDefault())

            paymentSource
                    .map { source -> source.expirationDate()?.let { simpleDateFormat.format(it) }?: "" }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.cardExpiration)

            paymentSource
                    .map { cardIssuer(it) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.cardIssuer)

            paymentSource
                    .map { it.lastFour()?: "" }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.cardLastFour)

            paymentSource
                    .map { cardLogo(it) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.cardLogo)

            backing
                    .map { it.backerCompletedAt() }
                    .map { ObjectUtils.isNotNull(it) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle<Boolean>())
                    .subscribe(this.receivedCheckboxChecked)

            backing
                    .compose<Pair<Backing, Project>>(combineLatestPair(backedProject))
                    // combine the project, backing, and checked boolean (<<Project,Backing>, Checked>) to make client call
                    .compose(takePairWhen<Pair<Backing, Project>, Boolean>(this.receivedCheckboxToggled))
                    .switchMap { this.apiClient.postBacking(it.first.second, it.first.first, it.second).compose(neverError()) }
                    .compose(bindToLifecycle())
                    .share()
                    .subscribe()

            val rewardIsReceivable = backing
                    .map { ObjectUtils.isNotNull(it.rewardId()) }

            val backingIsCollected = backing
                    .map { it.status() }
                    .map { it == Backing.STATUS_COLLECTED }

            rewardIsReceivable
                    .compose(combineLatestPair<Boolean, Boolean>(backingIsCollected))
                    .map { it.first && it.second }
                    .map { BooleanUtils.negate(it) }
                    .distinctUntilChanged()
                    .compose(bindToLifecycle())
                    .subscribe(this.receivedSectionIsGone)

            this.refreshProject
                    .compose(bindToLifecycle())
                    .subscribe {
                        this.notifyDelegateToRefreshProject.onNext(null)
                        this.swipeRefresherProgressIsVisible.onNext(true)
                    }

            val refreshTimeout = this.notifyDelegateToRefreshProject
                    .delay(10, TimeUnit.SECONDS)

            Observable.merge(refreshTimeout, backedProject.skip(1))
                    .map { false }
                    .compose(bindToLifecycle())
                    .subscribe(this.swipeRefresherProgressIsVisible)
        }

        private fun cardIssuer(paymentSource: Backing.PaymentSource) : Either<String, Int> {
            return when (CreditCardPaymentType.safeValueOf(paymentSource.paymentType())) {
                CreditCardPaymentType.ANDROID_PAY -> Either.Right(R.string.googlepay_button_content_description)
                CreditCardPaymentType.APPLE_PAY -> Either.Right(R.string.apple_pay_content_description)
                CreditCardPaymentType.CREDIT_CARD -> Either.Left(StoredCard.issuer(CreditCardTypes.safeValueOf(paymentSource.type())))
                else -> Either.Left(Card.CardBrand.UNKNOWN)
            }
        }

        private fun cardLogo(paymentSource: Backing.PaymentSource) : Int {
            return when (CreditCardPaymentType.safeValueOf(paymentSource.paymentType())) {
                CreditCardPaymentType.ANDROID_PAY -> R.drawable.google_pay_mark
                CreditCardPaymentType.APPLE_PAY -> R.drawable.apple_pay_mark
                CreditCardPaymentType.CREDIT_CARD -> StoredCard.getCardTypeDrawable(CreditCardTypes.safeValueOf(paymentSource.type()))
                else -> R.drawable.generic_bank_md
            }
        }

        private fun pledgeStatusData(project: Project) : PledgeStatusData {
            val statusStringRes = when (project.state()) {
                Project.STATE_CANCELED -> R.string.The_creator_canceled_this_project_so_your_payment_method_was_never_charged
                Project.STATE_FAILED -> R.string.This_project_didnt_reach_its_funding_goal_so_your_payment_method_was_never_charged
                else -> when (project.backing()?.status()) {
                    Backing.STATUS_CANCELED -> R.string.You_canceled_your_pledge_for_this_project
                    Backing.STATUS_COLLECTED -> R.string.We_collected_your_pledge_for_this_project
                    Backing.STATUS_DROPPED -> R.string.Your_pledge_was_dropped_because_of_payment_errors
                    Backing.STATUS_ERRORED -> R.string.We_cant_process_your_pledge_Please_update_your_payment_method
                    Backing.STATUS_PLEDGED -> R.string.If_the_project_reaches_its_funding_goal_you_will_be_charged_total_on_project_deadline
                    Backing.STATUS_PREAUTH -> R.string.We_re_processing_your_pledge_pull_to_refresh
                    else -> null
                }
            }

            val projectDeadline = project.deadline()?.let { DateTimeUtils.longDate(it) }
            val pledgeTotal = project.backing()?.amount()?.let { this.ksCurrency.format(it, project) }
            return PledgeStatusData(statusStringRes, pledgeTotal, projectDeadline)
        }

        override fun pledgeSuccessfullyUpdated() {
            this.showUpdatePledgeSuccess.onNext(null)
        }

        override fun project(project: Project) {
            this.projectInput.onNext(project)
        }

        override fun receivedCheckboxToggled(checked: Boolean) {
            this.receivedCheckboxToggled.onNext(checked)
        }

        override fun refreshProject() {
            this.refreshProject.onNext(null)
        }

        override fun backerAvatar(): Observable<String> = this.backerAvatar

        override fun backerName(): Observable<String> = this.backerName

        override fun backerNumber(): Observable<String> = this.backerNumber

        override fun cardExpiration(): Observable<String> = this.cardExpiration

        override fun cardIssuer(): Observable<Either<String, Int>> = this.cardIssuer

        override fun cardLastFour(): Observable<String> = this.cardLastFour

        override fun cardLogo(): Observable<Int> = this.cardLogo

        override fun notifyDelegateToRefreshProject(): Observable<Void> = this.notifyDelegateToRefreshProject

        override fun paymentMethodIsGone(): Observable<Boolean> = this.paymentMethodIsGone

        override fun pledgeAmount(): Observable<CharSequence> = this.pledgeAmount

        override fun pledgeDate(): Observable<String> = this.pledgeDate

        override fun pledgeStatusData(): Observable<PledgeStatusData> = this.pledgeStatusData

        override fun pledgeSummaryIsGone(): Observable<Boolean> = this.pledgeSummaryIsGone

        override fun projectAndReward(): Observable<Pair<Project, Reward>> = this.projectAndReward

        override fun receivedCheckboxChecked(): Observable<Boolean> = this.receivedCheckboxChecked

        override fun receivedSectionIsGone(): Observable<Boolean> = this.receivedSectionIsGone

        override fun shippingAmount(): Observable<CharSequence> = this.shippingAmount

        override fun shippingLocation(): Observable<String> = this.shippingLocation

        override fun shippingSummaryIsGone(): Observable<Boolean> = this.shippingSummaryIsGone

        override fun showUpdatePledgeSuccess(): Observable<Void> = this.showUpdatePledgeSuccess

        override fun swipeRefresherProgressIsVisible(): Observable<Boolean> = this.swipeRefresherProgressIsVisible

        override fun totalAmount(): Observable<CharSequence> = this.totalAmount
    }
}
