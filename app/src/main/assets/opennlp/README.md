# OpenNLP Models

This directory should contain the following OpenNLP model files:

## Required Models

1. **en-token.bin** - English Tokenizer Model
2. **en-pos-maxent.bin** - English POS Tagger Model

## Download Instructions

Download these models from the Apache OpenNLP official repository:

### English Tokenizer Model
- URL: https://dlcdn.apache.org/opennlp/models/ud-models-1.2/opennlp-en-ud-ewt-tokens-1.2-2.3.0.bin
- Save as: `en-token.bin`

### English POS Tagger Model
- URL: https://dlcdn.apache.org/opennlp/models/ud-models-1.2/opennlp-en-ud-ewt-pos-1.2-2.3.0.bin
- Save as: `en-pos-maxent.bin`

## Alternative Models (for other languages)

For multi-language support, you can download additional models:
- Spanish: https://dlcdn.apache.org/opennlp/models/ud-models-1.2/opennlp-es-ud-ancora-tokens-1.2-2.3.0.bin
- French: https://dlcdn.apache.org/opennlp/models/ud-models-1.2/opennlp-fr-ud-gsd-tokens-1.2-2.3.0.bin
- German: https://dlcdn.apache.org/opennlp/models/ud-models-1.2/opennlp-de-ud-gsd-tokens-1.2-2.3.0.bin

## Model File Structure

```
app/src/main/assets/opennlp/
├── README.md (this file)
├── en-token.bin
└── en-pos-maxent.bin
```

## Notes

- The application will use fallback logic if these models are not found
- Models are loaded from assets at runtime
- Total size: ~8-10 MB
