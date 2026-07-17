# CloudMold Operations Intelligence

This bounded context owns immutable source observations, reviewed intelligence clues and the lifecycle of operational alert cases.

It deliberately does not own model invocation evidence (`cloudmold-module-ai-operations`), risk policy/decision authority (`cloudmold-module-risk`), task execution (`cloudmold-module-metadata`) or any downstream business effect. Raw content, prompts, messages, titles, descriptions and personal identifiers are represented only by restricted evidence references and SHA-256 digests.

An AI-derived clue begins as `OBSERVED`; only an explicit human review can make it `ACCEPTED` or `REJECTED`. Alert status is versioned and append-only. None of these records authorizes account blocking, refunds, task mutation or other business actions.
