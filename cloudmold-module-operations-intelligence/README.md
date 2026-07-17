# CloudMold Operations Intelligence

This bounded context owns immutable source observations, reviewed intelligence clues and the lifecycle of operational alert cases.

It deliberately does not own model invocation evidence (`cloudmold-module-ai-operations`), risk policy/decision authority (`cloudmold-module-risk`), task execution (`cloudmold-module-metadata`) or any downstream business effect. Raw content, prompts, messages, titles, descriptions and personal identifiers are represented only by restricted evidence references and SHA-256 digests.

An AI-derived clue begins as `OBSERVED`; only an explicit human review can make it `ACCEPTED` or `REJECTED`. Alert status is versioned and append-only. None of these records authorizes account blocking, refunds, task mutation or other business actions.

Classification observations use contract version 2 and must bind an exact,
effective Risk-owned `IntelligenceEventTaxonomy` definition version plus one
member level. The persisted observation freezes the taxonomy source lineage,
definition effective time and chosen level. New observations against a retired
taxonomy fail closed; observations made before retirement remain historically
valid. Level labels such as `A/B/C` are categorical source vocabulary, not risk
severity or permission to execute an effect.
