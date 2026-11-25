package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyNotFoundException
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.post.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Resolves which object's image a party shows (its own, its post's, or none) and issues the OCI
 * download PARs for it.
 *
 * Deliberately not @Transactional: the DB reads finish in a short read-only transaction that returns
 * plain values, and the OCI calls run after it. This only keeps the pooled connection free because the
 * image routes are excluded from open-in-view (OpenInViewConfig); under open-in-view the request would
 * keep the connection it used for the reads until it ends. Pinned by ImageParConnectionIntegrationTest.
 */
@Service
class PartyImageDownloadService(
    private val partyRepository: PartyRepository,
    private val postRepository: PostRepository,
    private val partyImgService: PartyImgService,
    private val postImgService: PostImgService,
    transactionManager: PlatformTransactionManager,
) {
    private val readOnlyTransaction = TransactionTemplate(transactionManager).also { it.isReadOnly = true }

    fun resolveOriginal(partyUuid: UUID): ParResponse? {
        val source = readOnlyTransaction.execute { originalSourceOf(partyUuid) } ?: return null
        return when (source.owner) {
            ImageOwner.PARTY -> partyImgService.createPartyOriginalImgDownloadPars(source.uuid)
            ImageOwner.POST -> postImgService.createPostOriginalImgDownloadPars(source.uuid)
        }
    }

    /** Same length and order as [partyUuids]; null for an unknown party or one without an image. */
    fun resolveThumbnails(partyUuids: List<UUID>): List<ParResponse?> {
        val sources = readOnlyTransaction.execute { thumbnailSourcesOf(partyUuids) }
            ?: throw TransactionSystemException("Thumbnail source transaction returned no result")
        return sources.map { source -> source?.let(::createThumbnailPar) }
    }

    private fun createThumbnailPar(source: ImageSource): ParResponse? = when (source.owner) {
        ImageOwner.PARTY -> partyImgService.createPartyThumbnailImgDownloadPars(listOf(source.uuid)).firstOrNull()
        ImageOwner.POST -> postImgService.createPostThumbnailImgDownloadPars(listOf(source.uuid)).firstOrNull()
    }

    private fun originalSourceOf(partyUuid: UUID): ImageSource? {
        val party = partyRepository.findByUuid(partyUuid) ?: throw PartyNotFoundException(partyUuid)
        return when (party.imageState) {
            PartyImageState.CUSTOM -> ImageSource(ImageOwner.PARTY, party.uuid)
            PartyImageState.NONE -> null
            PartyImageState.INHERITED -> postRepository.findByParty_Uuid(partyUuid)?.let { ImageSource(ImageOwner.POST, it.uuid) }
        }
    }

    // Parties first: the posts' lazy party references then resolve from the same persistence context
    // instead of one query per post.
    private fun thumbnailSourcesOf(partyUuids: List<UUID>): List<ImageSource?> {
        val partyByUuid = partyRepository.findAllByUuidIn(partyUuids).associateBy { it.uuid }
        val postUuidByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associate { it.party.uuid to it.uuid }
        return partyUuids.map { uuid ->
            val party = partyByUuid[uuid] ?: return@map null
            when (party.imageState) {
                PartyImageState.CUSTOM -> ImageSource(ImageOwner.PARTY, party.uuid)
                PartyImageState.NONE -> null
                PartyImageState.INHERITED -> postUuidByPartyUuid[uuid]?.let { ImageSource(ImageOwner.POST, it) }
            }
        }
    }

    private enum class ImageOwner { PARTY, POST }

    private data class ImageSource(val owner: ImageOwner, val uuid: UUID)
}
