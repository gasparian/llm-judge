# llm-judge

![Build](https://github.com/gasparian/llm-judge/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)


<!-- Plugin description -->
llm-judge is a PyCharm plugin that turns your IDE into an LLM-as-a-judge tool. Load a `judge.json` dataset, run your local Python model, and have OpenAI score each output. View Inputs, References, Model Outputs, and Scores in a simple tool window with Run/Cancel controls.
<!-- Plugin description end -->

## Installation

### 1. From the IDE Marketplace
1. **Settings/Preferences > Plugins**
2. Switch to **Marketplace**
3. Search for **“LLM Judge”**
4. Click **Install** and restart your IDE

### 2. From JetBrains Marketplace
1. Visit [https://plugins.jetbrains.com/plugin/llm-judge](https://plugins.jetbrains.com/plugin/llm-judge)
2. Click **Install to...** (if your IDE is running)
3. Or download the ZIP from the **Versions** tab and use **Install plugin from disk…**

### 3. Manual Download
1. Download the latest ZIP from the [GitHub Releases page](https://github.com/gasparian/llm-judge/releases/latest)
2. **Settings/Preferences > Plugins > ⚙️ > Install plugin from disk…**
3. Select the downloaded file and restart your IDE

---

## Usage

1. **Create your dataset file**  
   At the root of your PyCharm project, add a `judge.json` file with this structure:
   ```json
   {
     "model_path": "path/to/your_model.py",
     "data": [
       {
         "input": "What is the capital of France?",
         "reference_output": "Paris"
       },
       {
         "input": "Translate 'Hello' to Spanish",
         "reference_output": "Hola"
       }
       // …more entries…
     ]
   }
   ```
   * `model_path` is the relative path (from the project root) to your Python script;
   * `data` is an array of objects, each with input and reference_output fields.
2. Set environment variables  

   The only required env. var - is OpenAI api key.
   The plugin uses openai models to score your model outputs. Before launching PyCharm (or running via Gradle), ensure the environment variable is set:  
   ```sh
   export OPENAI_API_KEY="sk-…"
   ```
   The other env. vars - are for concurrency limitations:
     * `LLM_JUDGE_MAX_PARALLEL` - to limit thread-pool size for `Dispatcher.IO`. Defualt is `5`;    
     * `LLM_JUDGE_MAX_API_CALLS` - basically, the batch size for api calls to OpenAI. Default is `5` as well;     
   
3. Run the evaluation
   * Open PyCharm and go to View → Tool Windows → LLM Judge 
   * Click Run in the tool window’s toolbar 
     * Your Python script will be invoked for each entry 
     * Once model outputs appear, the plugin will call OpenAI to score them
   * Click Stop at any time to cancel in-flight evaluations

With your `judge.json` in place and `OPENAI_API_KEY` configured, LLM Judge will automatically pick up the file
and display Inputs, References, Model Outputs, and Scores in its tool window.  

## Development

Clone the repository and use the Gradle wrapper to build, test, format, and launch a sandboxed IDE:

Static analysis / formatting:
```sh
./gradlew ktlintCheck
./gradlew ktlintFormat
```
Clean build:
```
./gradlew --refresh-dependencies clean build
```

Run simple unit tests:  
```sh
./gradlew test
```

Run a sandboxed IDE (requires OPENAI_API_KEY in your environment):
```sh
OPENAI_API_KEY=$OPENAI_API_KEY ./gradlew --refresh-dependencies clean runIde
```  
In order to test the plugin, I've included test python project to the current repo - 
you can just open it in the sandbox PyCharm at `python-test-model` and then repeat actions from the `Usage` part.  

Package the plugin for distribution
```sh
./gradlew buildPlugin
```

Plugin based on the IntelliJ Platform [Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
