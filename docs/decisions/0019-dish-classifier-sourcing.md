# 0019. Sourcing a dish classifier: what exists, what it is licensed for, and what it can name

Date: 20 September 2026. Status: **survey, no decision.** The decision is Vedant's. Nothing is
implemented and nothing is bundled. Owner of the survey: Arjun (`ml/vision/`).

Vedant ruled that `DishClassifier` stays. The constraint that made me recommend dropping it is
unchanged (no model, no mapping), so the first task was sourcing, to the standard `0005` set:
what the model is, who published it, what licence it carries **read from the source**, what
classes it covers, and whether those classes reach food codes already in our database **without
inventing a mapping**. This record is that survey.

Two constraints that do not move, from Vedant: the output is a first guess that voice corrects
(spec 12.4), never presented as a measurement; and hard rule 6 holds: no stub, no generic
labeller dressed as a dish classifier, no invented mapping.

## How "maps without inventing a mapping" was measured

A classifier label is a name. The food database already turns names into food codes through
its alias table, with the wrong-food guard, for the voice path (`SqliteFoodLookup.resolve`,
`0006`). So the mapping question has a measurement, not an opinion: run every label of every
candidate through the shipped alias table as-is and count what resolves. That is what the voice
path would do with the same string, and it adds no table of its own.

Measured against `app/src/main/assets/food/katori-food.db` (the shipped file, 50 recipes, 81
foods) with the matcher's own normalisation, exact alias hits only:

| Candidate | Labels | Resolve exactly | To |
| --- | ---: | ---: | --- |
| Google `aiy/vision/classifier/food_V1` | 2,023 | **15** | idli, sambar, upma, punugulu, samosa, pakoda, poori, chana_masala, chicken_curry, mutton_curry, omelette, rice_kheer, lemon_rice (`Chitranna`), jowar_roti (`Jolada rotti`), and `White rice` → **rice_raw** |
| Khana (80) | 80 | 14 | egg_curry, veg_biryani, buttermilk, chana_masala, chapati, fish_pulusu, idli, rice_kheer, masala_dosa, plain_vada, pakoda, poha_upma, pongal, samosa |
| Kaggle 80-class set (`dima806`, `Sarthak003` models) | 80 | 5 | veg_biryani, chana_masala, chapati, toor_dal_tadka, poha_upma |
| Kaggle 20-class set (`rajistics`, `DrishtiSharma` models) | 20 | 4 | chapati, idli, masala_dosa, samosa |
| `mindflayer/south-indian-foods` | 5 | 4 | idli, plain_dosa, sambar, plain_vada |
| `therealcyberlord/vit-indian-food` (15) | 15 | 4 | veg_biryani, toor_dal_tadka, plain_dosa, pakoda |
| `Amrrs/south-indian-foods` | 5 | 2 | idli, plain_vada |

### Containment is where the wrong foods are

`resolve` also matches an alias appearing inside a longer utterance, one direction, on word
boundaries (`0006`, hard rule 15). That is right for "I had chicken curry at the canteen" and
it is wrong for a dish label. Measured on Google's 2,023 labels: **135 resolve by containment
only, and most of them to the wrong food.**

| Label | Resolves to | Verdict |
| --- | --- | --- |
| `Fried chicken`, `Tandoori chicken`, `Butter chicken`, +30 more | `chicken`, the raw ingredient | wrong |
| `Palak paneer` | `palakura`, spinach leaves | wrong |
| `Dal makhani`, `Dal bhat` | `toor_dal_tadka` | wrong |
| `Roti jala`, `Sel roti` | `chapati` | wrong |
| `Hyderabadi biryani` | `veg_biryani` | wrong |
| `Peanut butter and jelly sandwich`, `Gooey butter cake` | `butter` | wrong |
| `Rice Krispies Treats`, `Fried rice`, `Jollof rice`, +15 more | `rice_cooked` | wrong |
| `Aloo gobi` | `cauliflower` and `potato`, a tie | ambiguous |
| `Chole bhature` | `chana_masala` | arguable |
| `Ragi mudde` | NO-DATA `ragi` | **correct refusal** |

So "map through the existing alias table" is safe for an EXACT alias hit and unsafe for a
containment hit. If a classifier is ever bound, its label goes through the alias table exactly,
never by containment and never fuzzily, which is a restriction of the existing mapping rather
than a new one. With that restriction Google's model reaches 15 recipes and nothing else.

**Finding for the data owner, independent of any classifier:** the alias `white rice` resolves
to `rice_raw`, uncooked grain. Spoken or photographed, "white rice" is a plate. That is a
wrong-food path on the voice side today; it goes to whoever owns the alias table.

## The candidates, one by one

### 1. Google, `aiy/vision/classifier/food_V1` — the only one whose publisher can license it

- **What:** MobileNet V1, 224×224 RGB in `[0,1]`, 2,024 outputs (background + 2,023 dishes).
  TFLite with embedded metadata; the label map is inside the file (`probability-labels-en.txt`).
  21,151,551 bytes float, sha256 `03dd6d9129501f97be00775d9e17b5d9ad13be730149352adb8f4ca953b7a650`.
- **Publisher:** Google. Published 6 October 2020 on TF Hub, now Kaggle Models
  (`google/aiy/tfLite/vision-classifier-food-v1`).
- **Licence, read from three places:** the Kaggle model instance record, `licenseName: "Apache
  2.0"`; the model card, verbatim: *"This model follows the Apache 2.0 license. If you intend to
  use it beyond permissable usage, please consult with the model owners ahead of time."*; and the
  TFLite file's own metadata: `license: Apache-2.0`, `author: Google`, `version: 1.0.0`. Google
  trained it and Google licenses it. This is the only candidate where the party granting the
  licence is the party that can.
- **Stated limitations, verbatim from the card:** *"This model was trained on a dataset skewed
  toward North American foods."* *"This model assumes that its input image contains a
  well-cropped food dish."* *"Do not use this model to predict allergen or nutrition
  information."* The last one is exactly our design: the model names, the database numbers.
- **Coverage of our plate:** about 38 of its 2,023 labels are Indian dishes. It has Idli,
  Sambar, Upma, Punugulu, Samosa, Pakora, Puri, Kheer, Chitranna, Jolada rotti, Chicken curry,
  Mutton curry, Omelette, Chana masala, Hyderabadi biryani, Dahi vada, Khichdi, Kadhi, Chole
  bhature, Palak paneer, Paneer tikka, Aloo paratha. **It has no dosa, no chapati or roti (only
  Bhakri, Roti jala and Jolada rotti), no plain vada, no rasam, no poha, no pongal, no chutney,
  no curd rice, no pesarattu, no bajji.** That is most of a Telugu breakfast. 35 of our 50
  recipes have no class at all.
- **What that means for the demo:** on a dosa the top classes will be whatever 2,023 foreign
  dishes look most like a dosa, none of which resolve, so the candidate list is empty and the
  app asks, which is the primary path anyway. On idli-sambar it may name both. The resolvable
  guess rate on a South Indian plate is low and **unmeasured**; the probe below measures it.
- **Download:** anonymous, no Kaggle login: the Kaggle Models download endpoint 302s to a signed
  GCS URL. `tools/4-fetch-models` can fetch it the way it fetches the others.
- **Runtime:** it is a TFLite-with-metadata image classifier, which the ML Kit family already in
  the build runs through `image-labeling-custom` (bundled variant, not the Play-services thin
  one, for the offline guarantee), or MediaPipe Tasks `ImageClassifier`, which is the same
  artefact `PoseEngine` would need. Either is a new dependency, and the merged-manifest
  whitelist check decides whether it brings a permission. Not verified; not started.

### 2. Khana (Omkar Prabhu, September 2025) — the best Indian dataset, and not usable as a model

- **What:** ~131,000 images, 80 dishes, arXiv 2509.06006, hosted at khana.omkarprabhu.in.
  Baselines reported (ConvNeXT-S 86.72% top-1) but **no weights are released**.
- **Publisher:** one individual, no institution.
- **Licence, verbatim from the dataset page:** *"Khana does not own the copyright of the images.
  Khana only compiles an accurate list of web images for each food dish. It is available for
  researchers and educators who wish to use the images for non-commercial research and/or
  educational purposes only."* The paper says the images were *"scraped from search engines and
  online food delivery platforms like Swiggy and Zomato"*.
- **Verdict:** a hackathon build could train on it under the non-commercial allowance `0005`
  reserves, but that means a training job, a 6.4 GB download, and shipping weights derived from
  images whose copyright the compiler disclaims. Coverage is the best of any candidate (14 of 80
  classes resolve exactly, including masala dosa, medu vada, pongal, poha and chapati), and it
  still has no plain dosa, no rasam and no chutney. If Vedant wants a classifier that knows the Telugu plate, this is the dataset to
  train on, and it is a training project, not a sourcing one.

### 3. The Kaggle datasets and the Hugging Face models trained on them — labels without a grant

Read through Kaggle's public dataset API (`licenseName` field) and each dataset's own
description:

| Dataset | Kaggle licence field | What the description says |
| --- | --- | --- |
| `l33tc0d3r/indian-food-classification` (20 classes, 1.6 GB) | `MIT` | *"All the images are extracted from google."* |
| `iamsouravbanerjee/indian-food-images-dataset` (80 classes, 4,000 images) | `Other (specified in description)` | the description specifies nothing; *"This Dataset is created from Google Images"* |
| `theeyeschico/indian-food-classification` (20) | `Data files © Original Authors` | no grant |
| `dataclusterlabs/indian-food-image-dataset` | `Data files © Original Authors` | a sample; full set sold by mail |
| HF `bharat-raghunathan/indian-foods-dataset` (15) | `cc0-1.0` | *"Collection by Scraping data from Google Images"* |
| HF `rajistics/indian_food_images` (20) | none | *"all the images are extracted from google"*, credits `l33tc0d3r` |

An uploader cannot MIT or CC0 photographs they took from Google Images. This is the `ifct2017`
npm argument from spec 13.1 applied to pictures: he can license his code; he cannot license
someone else's images. Every Hugging Face model in the "indian food" search (50 results, all
ViT or SigLIP fine-tunes, likes ≤ 4, downloads ≤ 181) is trained on one of these sets. Their
`apache-2.0` tags are the uploaders' claims on weights derived from data with no grant. The two
80-label models (`dima806`, `Sarthak003`) carry the `iamsouravbanerjee` label list verbatim; the
20-label ones carry `l33tc0d3r`'s. They are also ViT-base, ~330 MB float, not phone
classifiers. **None is usable under `0005`'s standard.**

### 4. Ruled out on coverage before licence

- ML Kit Image Labeling (generic, ~400 labels): rejected in `0018`, and Vedant's constraint
  names it.
- MediaPipe `EfficientNet-Lite` ImageNet classifiers: no dish classes.
- Food-101 (ETH Zürich): 101 classes, a handful Indian, images from foodspotting.com,
  non-commercial research. Not a candidate.

## What would have to be true for the Google model to ship

0. **Exact alias hits only.** The containment table above is the reason. This is a rule for the
   classifier's caller, and the probe asserts it.
1. **It resolves something on our plates.** Unknown. The probe is the same shape as
   `PhotographedReportProbeTest`: photographs of real plates staged beside the models, a person
   writes what is on the plate, the classifier's top-3 go through `resolve`, and the table counts
   RIGHT RECIPE, WRONG RECIPE and NO CANDIDATE. WRONG RECIPE is the number that matters, as WRONG
   FOOD is for the matcher. Nobody should bind this classifier before that table exists.
2. **A score threshold that is measured, not chosen.** The contract says "nothing recognised
   above threshold: an empty candidate list". The threshold comes from the probe's score
   distribution on real plates, or the classifier does not ship.
3. **A dependency that passes the permission whitelist.** ML Kit `image-labeling-custom` or
   MediaPipe Tasks, bundled. The build decides on the merged manifest.
4. **`0005`'s register:** Apache-2.0, no non-commercial allowance needed, a line in the model
   table with the sha256 above.

What it would cost: one `ml/vision/` class over the runtime, one provider line in `di/` (Rao's),
one probe, one `tools/4-fetch-models` entry (Nila's), and Rao's time with plates in front of the
phone. The code is a day; the measurement is what the day is for.

## The recommendation, and the decision that is not mine

Only one model can be sourced cleanly, and it does not know dosa, roti or vada. Everything that
knows the Telugu plate is either a dataset that needs training and disclaims its own images, or
a model whose licence is not the uploader's to give.

So the honest options are three, and choosing is Vedant's:

- **A. Bind Google's model, measured first.** Clean licence, on-device, tiny integration.
  Expect it to name idli, sambar, upma, samosa and to be silent on dosa and roti. The demo
  shows a first guess where one exists and asks where none does, which spec 12.4 already frames
  as the design. Ship only if the probe's WRONG RECIPE line is acceptable.
- **B. Train on Khana** under the non-commercial allowance, for a model that knows the plate.
  A training project with a licence line that reads "non-commercial, images of disclaimed
  copyright" in `0005`'s register, and a hackathon-week cost that beat 3 does not have to spare.
- **C. Drop it**, as `0018` recommended, with this survey as the reason.

Not built, not bundled, not bound. This record is the report Vedant asked for before any of it.

## Sources, as read

- Kaggle model instance record: `https://www.kaggle.com/api/v1/models/google/aiy/tfLite/vision-classifier-food-v1/get` (`licenseName: "Apache 2.0"`, `totalUncompressedBytes: 21151551`)
- Model card: `https://www.kaggle.com/api/v1/models/google/aiy/get` (description, limitations, licence sentence)
- Label map: `https://www.gstatic.com/aihub/tfhub/labelmaps/aiy_food_V1_labelmap.csv` (2,024 rows)
- The TFLite file's embedded metadata, read from the downloaded archive
- Khana: `https://arxiv.org/html/2509.06006v1`, `https://khana.omkarprabhu.in/`, `https://khana.omkarprabhu.in/files/labels.txt`
- Kaggle dataset records: `https://www.kaggle.com/api/v1/datasets/view/<owner>/<slug>` for the four sets above
- Hugging Face: `https://huggingface.co/api/models?search=indian%20food&pipeline_tag=image-classification`, each model's `config.json` for its label map, `https://huggingface.co/datasets/bharat-raghunathan/indian-foods-dataset`, `https://huggingface.co/datasets/rajistics/indian_food_images`
