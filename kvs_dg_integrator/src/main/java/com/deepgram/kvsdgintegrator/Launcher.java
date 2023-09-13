package com.deepgram.kvsdgintegrator;

import com.fasterxml.jackson.core.JsonProcessingException;

public class Launcher {
	public static void main(String[] args) {
		System.out.println("Launching task");
		String integratorArgumentsJson = System.getenv("INTEGRATOR_ARGUMENTS");
		if (integratorArgumentsJson == null) {
			System.out.println("ERROR: this task expects an environment variable INTEGRATOR_ARGUMENTS");
			return;
		} else {
			System.out.println("INTEGRATOR_ARGUMENTS=" + integratorArgumentsJson);
		}

		IntegratorArguments integratorArguments;
		try {
			integratorArguments = IntegratorArguments.fromJson(integratorArgumentsJson);
		} catch (JsonProcessingException e) {
			System.out.println("ERROR: couldn't parse INTEGRATOR_ARGUMENTS: " + e);
			return;
		}

		String deepgramApiKey = System.getenv("DEEPGRAM_API_KEY");
		if (deepgramApiKey == null) {
			System.out.println("ERROR: this task expects an environment variable DEEPGRAM_API_KEY");
			return;
		}

		try {
			KvsToDgStreamer.startKvsToDgStreaming(integratorArguments, deepgramApiKey);
		} catch (Exception e) {
			System.out.println("ERROR: exception while attempting to stream audio: " + e);
		}
	}
}
