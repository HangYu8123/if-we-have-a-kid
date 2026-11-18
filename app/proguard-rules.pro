# kotlinx.serialization ships its own R8 rules. Keep the simulation model names stable for saved runs.
-keep class app.childrensimulator.sim.model.** { *; }
