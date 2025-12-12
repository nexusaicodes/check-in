# OpenNLP Models

This directory should contain the following OpenNLP model files:

## Required Models

1. **en-token.bin** - English Tokenizer Model
2. **en-pos-maxent.bin** - English POS Tagger Model

## Download Instructions

Download these models from the Apache OpenNLP official repository (version 1.5 models for OpenNLP 1.9.4):

### English Tokenizer Model
- URL: http://opennlp.sourceforge.net/models-1.5/en-token.bin
- Save as: `en-token.bin`
- File size: ~320 KB

### English POS Tagger Model
- URL: http://opennlp.sourceforge.net/models-1.5/en-pos-maxent.bin
- Save as: `en-pos-maxent.bin`
- File size: ~1.1 MB

**Note:** We use OpenNLP 1.9.4 (not 2.x) for Android compatibility. Models from 1.5 series work perfectly with 1.9.x.

## Quick Download Commands

```bash
cd app/src/main/assets/opennlp/
curl -O http://opennlp.sourceforge.net/models-1.5/en-token.bin
curl -O http://opennlp.sourceforge.net/models-1.5/en-pos-maxent.bin
```

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
