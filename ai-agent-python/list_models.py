import os
import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()

api_key = os.getenv("GEMINI_API_KEY")
if not api_key:
    print("Error: GEMINI_API_KEY not set in .env")
    exit(1)

genai.configure(api_key=api_key)

print(f"Checking models for API key ending in ...{api_key[-4:]}")

print("\nAvailable models supporting 'generateContent':")
try:
    found = False
    for m in genai.list_models():
        if 'generateContent' in m.supported_generation_methods:
            print(f"- {m.name}")
            found = True
    
    if not found:
        print("No models found that support generateContent.")

except Exception as e:
    print(f"Error listing models: {e}")
