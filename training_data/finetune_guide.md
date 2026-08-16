# Fine-tuning Guide — Agentic OS Instagram Model

## Option 1: Together.ai (Easiest, ~$1-3)

### Step 1 — Account
Go to: https://api.together.xyz
Sign up → Get API key

### Step 2 — Upload Dataset
```bash
pip install together
together files upload instagram_training_dataset.jsonl
```

### Step 3 — Start Fine-tuning
```python
import together

together.api_key = "YOUR_TOGETHER_KEY"

response = together.Finetune.create(
    training_file = "file-xxxx",  # from upload response
    model         = "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo",
    n_epochs      = 3,
    batch_size    = 4,
    learning_rate = 1e-5,
    suffix        = "agentic-os-instagram"
)
print(response)
```

### Step 4 — Use Your Model
```python
# After training (takes ~30 min)
model_name = "your-username/Meta-Llama-3.1-8B-Instruct-Turbo-agentic-os-instagram"

# Update GroqClientConfig.kt:
# const val MODEL = "your-fine-tuned-model-name"
# const val BASE_URL = "https://api.together.xyz/v1/chat/completions"
```

---

## Option 2: Hugging Face + Google Colab (Free)

### Step 1 — Open Colab
Go to: https://colab.research.google.com
New notebook → Change runtime to T4 GPU (free)

### Step 2 — Install Dependencies
```python
!pip install transformers datasets peft trl
```

### Step 3 — Fine-tune Qwen2.5
```python
from datasets import load_dataset
from trl import SFTTrainer
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig

# Load dataset
dataset = load_dataset("json", data_files="instagram_training_dataset.jsonl")

# Load model
model_name = "Qwen/Qwen2.5-1.5B-Instruct"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForCausalLM.from_pretrained(model_name)

# Fine-tune with LoRA
lora_config = LoraConfig(r=16, lora_alpha=32, target_modules=["q_proj","v_proj"])

trainer = SFTTrainer(
    model=model,
    train_dataset=dataset["train"],
    peft_config=lora_config,
    max_seq_length=512,
)
trainer.train()
trainer.save_model("agentic-os-model")
```

### Step 4 — Push to HuggingFace Hub
```python
from huggingface_hub import HfApi
api = HfApi()
api.upload_folder(
    folder_path="agentic-os-model",
    repo_id="your-username/agentic-os-instagram",
    repo_type="model"
)
```

### Step 5 — Use in Ollama
```bash
# Create Modelfile
echo "FROM hf.co/your-username/agentic-os-instagram" > Modelfile
ollama create agentic-os -f Modelfile
ollama run agentic-os
```

---

## Dataset Statistics
- Total examples: 120+
- Languages: English, Roman Urdu
- Intents covered: 13
- Instagram actions: 10
- Phone actions: 3

## Intents in Dataset
| Intent | Examples |
|--------|---------|
| INSTAGRAM_LIKE | 15 |
| INSTAGRAM_COMMENT | 10 |
| INSTAGRAM_FOLLOW | 9 |
| INSTAGRAM_UNFOLLOW | 8 |
| INSTAGRAM_POST | 11 |
| INSTAGRAM_STORY | 9 |
| INSTAGRAM_REEL | 9 |
| INSTAGRAM_DM | 8 |
| INSTAGRAM_SCROLL | 8 |
| INSTAGRAM_SEARCH | 5 |
| INSTAGRAM_OPEN_PROFILE | 5 |
| OPEN_APP | 10 |
| CALL_CONTACT | 3 |
| SEARCH | 3 |
| CONVERSATION | 6 |
| OPEN_SETTINGS | 2 |
| UNKNOWN | 2 |
