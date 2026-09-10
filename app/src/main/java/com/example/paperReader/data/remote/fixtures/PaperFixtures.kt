package com.example.paperReader.data.remote.fixtures

import com.example.paperReader.data.remote.RemoteJson
import com.example.paperReader.data.remote.dto.PaperDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import kotlinx.serialization.decodeFromString

/**
 * Deterministic placeholder catalog shaped exactly like a future PaperReader backend response.
 * Fixtures are raw JSON text (not in-memory DTO construction) so they pass through the same
 * kotlinx.serialization decoding, validation, and mapping path a live HTTP response would use.
 */
object PaperFixtures {

    /** One entry per demonstrable access/metadata case: DIRECT_PDF, OPEN_FULL_TEXT, NO_FREE_FULL_TEXT,
     * PAYWALLED, an unrecognized future access value (maps to UNKNOWN), a paper missing every optional
     * field, and a second OPEN_FULL_TEXT entry so pagination spans three pages at [PAGE_SIZE] = 3. */
    val catalog: List<PaperDto> by lazy {
        RemoteJson.decodeFromString<List<PaperDto>>(catalogJson)
    }

    val emptySearchResponse: PaperSearchResponseDto by lazy {
        RemoteJson.decodeFromString<PaperSearchResponseDto>(emptySearchResponseJson)
    }

    private val catalogJson = """
    [
      {
        "id": "W2741809807",
        "title": "Harnessing Generative AI for Automated Feedback in Higher Education",
        "authors": [{"name": "R. M. Mahoney"}, {"name": "A. Chen"}],
        "venue": "Online Learning",
        "year": 2024,
        "type": "REVIEW",
        "topics": [{"name": "Education"}],
        "citation_count": 151,
        "access": {
          "state": "DIRECT_PDF",
          "descriptor": {
            "url": "https://demo.paperreader.invalid/papers/W2741809807.pdf",
            "mime_type": "application/pdf",
            "provenance": "Demo repository descriptor"
          }
        }
      },
      {
        "id": "W3123456789",
        "title": "Towards Sustainable Agriculture: Harnessing AI for Global Food Security",
        "authors": [{"name": "A. Javaid"}],
        "venue": "Artificial Intelligence in Agriculture",
        "year": 2024,
        "type": "ARTICLE",
        "topics": [{"name": "Agriculture"}],
        "citation_count": 171,
        "access": {
          "state": "OPEN_FULL_TEXT",
          "source_url": "https://demo.paperreader.invalid/papers/W3123456789",
          "provenance": "Demo open HTML descriptor"
        }
      },
      {
        "id": "W3987654321",
        "title": "Empowering SMEs with Generative AI for Resilience",
        "authors": [{"name": "P. Chatterjee"}],
        "venue": "Management Research Review",
        "year": 2024,
        "type": "ARTICLE",
        "topics": [{"name": "Business"}],
        "citation_count": 82,
        "access": {"state": "NO_FREE_FULL_TEXT"}
      },
      {
        "id": "W4014455667",
        "title": "Generative AI Adoption Barriers in Clinical Radiology Workflows",
        "authors": [{"name": "S. Okafor"}, {"name": "L. Fenwick"}],
        "venue": "Journal of Medical Imaging Informatics",
        "year": 2023,
        "type": "ARTICLE",
        "topics": [{"name": "Healthcare"}],
        "citation_count": 64,
        "access": {"state": "PAYWALLED"}
      },
      {
        "id": "W4022233445",
        "title": "Longitudinal Effects of Generative AI Tutoring on Retention",
        "authors": [{"name": "T. Nakamura"}],
        "venue": "Learning and Instruction",
        "year": 2025,
        "type": "PREPRINT",
        "topics": [{"name": "Education"}],
        "citation_count": 9,
        "access": {"state": "EMBARGOED"}
      },
      {
        "id": "W4031122556",
        "title": "A Field Note on Generative AI in Community Journalism",
        "authors": [{"name": "M. Ibrahim"}]
      },
      {
        "id": "W4040099887",
        "title": "Generative AI Literacy Frameworks for Public Libraries",
        "authors": [{"name": "D. Souza"}, {"name": "K. Whitfield"}],
        "venue": "Journal of Library Innovation",
        "year": 2022,
        "type": "ARTICLE",
        "topics": [{"name": "Library Science"}],
        "citation_count": 37,
        "access": {
          "state": "OPEN_FULL_TEXT",
          "source_url": "https://demo.paperreader.invalid/papers/W4040099887",
          "provenance": "Demo open HTML descriptor"
        }
      }
    ]
    """.trimIndent()

    private val emptySearchResponseJson = """
    {"items": [], "next_cursor": null, "total_count": 0}
    """.trimIndent()
}
