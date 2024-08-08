<p>The dynamic part is based on UFO (<a href="https://github.com/xinguohua/UFO">UFO</a>) and includes LLVM instrumentation and prediction components. First, the instrumentation component modifies ThreadSanitizer in LLVM to insert necessary instrumentation into the bytecode file for logging purposes. Next, the prediction tool analyzes the logs recorded by the instrumentation and predicts potential instruction reordering issues.</p>
<p>You can execute these steps by running a script, for example:</p>
<pre><code>./dynamic_run.sh 22-openmpi.c
</code></pre>
<p>The repository for the instrumentation part can be found at <a href="https://anonymous.4open.science/r/SVF-Data-Race-Detection-Tool-5976">Instrumentation Code</a>.</p>
<p>The repository for the prediction part can be found at <a href="https://anonymous.4open.science/r/Dynamic-Analysis-Tool-1234">Prediction Code</a>.</p>
