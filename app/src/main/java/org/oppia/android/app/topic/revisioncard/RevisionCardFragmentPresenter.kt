package org.oppia.android.app.topic.revisioncard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.oppia.android.app.fragment.FragmentScope
import org.oppia.android.app.model.EventLog
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.topic.conceptcard.ConceptCardFragment
import org.oppia.android.app.topic.conceptcard.ConceptCardFragment.Companion.CONCEPT_CARD_DIALOG_FRAGMENT_TAG
import org.oppia.android.app.viewmodel.ViewModelProvider
import org.oppia.android.databinding.RevisionCardFragmentBinding
import org.oppia.android.domain.oppialogger.OppiaLogger
import org.oppia.android.domain.translation.TranslationController
import org.oppia.android.util.gcsresource.DefaultResourceBucketName
import org.oppia.android.util.parser.html.HtmlParser
import org.oppia.android.util.parser.html.TopicHtmlParserEntityType
import org.oppia.android.util.system.OppiaClock
import javax.inject.Inject

/** Presenter for [RevisionCardFragment], sets up bindings from ViewModel. */
@FragmentScope
class RevisionCardFragmentPresenter @Inject constructor(
  private val fragment: Fragment,
  private val oppiaLogger: OppiaLogger,
  private val oppiaClock: OppiaClock,
  private val htmlParserFactory: HtmlParser.Factory,
  @DefaultResourceBucketName private val resourceBucketName: String,
  @TopicHtmlParserEntityType private val entityType: String,
  private val viewModelProvider: ViewModelProvider<RevisionCardViewModel>,
  private val translationController: TranslationController
) : HtmlParser.CustomOppiaTagActionListener {
  private lateinit var profileId: ProfileId

  fun handleCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    topicId: String,
    subtopicId: Int,
    profileId: ProfileId
  ): View? {
    this.profileId = profileId

    val binding =
      RevisionCardFragmentBinding.inflate(
        inflater,
        container,
        /* attachToRoot= */ false
      )
    val view = binding.revisionCardExplanationText
    val viewModel = getReviewCardViewModel()

    viewModel.initialize(topicId, subtopicId, profileId)
    logRevisionCardEvent(topicId, subtopicId)

    binding.let {
      it.viewModel = viewModel
      it.lifecycleOwner = fragment
    }

    viewModel.revisionCardLiveData.observe(
      fragment,
      { ephemeralRevisionCard ->
        val pageContentsHtml =
          translationController.extractString(
            ephemeralRevisionCard.revisionCard.pageContents,
            ephemeralRevisionCard.writtenTranslationContext
          )
        view.text = htmlParserFactory.create(
          resourceBucketName, entityType, topicId, imageCenterAlign = true,
          customOppiaTagActionListener = this
        ).parseOppiaHtml(
          pageContentsHtml, view, supportsLinks = true, supportsConceptCards = true
        )
      }
    )

    return binding.root
  }

  /** Dismisses the concept card fragment if it's currently active in this fragment. */
  fun dismissConceptCard() {
    fragment.childFragmentManager.findFragmentByTag(
      CONCEPT_CARD_DIALOG_FRAGMENT_TAG
    )?.let { dialogFragment ->
      fragment.childFragmentManager.beginTransaction().remove(dialogFragment).commitNow()
    }
  }

  private fun getReviewCardViewModel(): RevisionCardViewModel {
    return viewModelProvider.getForFragment(fragment, RevisionCardViewModel::class.java)
  }

  private fun logRevisionCardEvent(topicId: String, subTopicId: Int) {
    oppiaLogger.logTransitionEvent(
      oppiaClock.getCurrentTimeMs(),
      EventLog.EventAction.OPEN_REVISION_CARD,
      oppiaLogger.createRevisionCardContext(topicId, subTopicId)
    )
  }

  override fun onConceptCardLinkClicked(view: View, skillId: String) {
    ConceptCardFragment
      .newInstance(skillId, profileId)
      .showNow(fragment.childFragmentManager, CONCEPT_CARD_DIALOG_FRAGMENT_TAG)
  }
}
