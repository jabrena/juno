---
title: "Supabase: publish Grove temperatures to the cloud"
description: "A Wi-Fi temperature publisher that sends Grove sensor readings to Supabase through its PostgREST API."
date: 2026-11-14
layout: post
link: /blog/supabase/
image: grove/grove-temperature-v12.jpg
tags: [supabase, wifi, sensors]
---

[`SupabaseGroveTemperatures`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/supabase/publisher/SupabaseGroveTemperatures.java)
reads three Grove temperature sensors once a minute and inserts them as one row of a Supabase table through its
PostgREST API. It shows Wi-Fi, HTTPS requests with caller-owned buffers, and a timestamp taken once from
`LocalDateTime.now()` and then advanced by the board's clock, so the program makes no extra time requests.

A failing sensor is sent as `null`, so one broken sensor does not stop the other two from being published. The board is
a public client, so it uses a publishable key that is protected by Row Level Security. The
[UNO R4 WiFi](../../arduino-uno-r4-wifi) page describes the wiring.

| Field | Value |
|---|---|
| Sources | [`usecases/supabase`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/supabase) |
| Boards supported | [`UNO R4 WiFi`](../../arduino-uno-r4-wifi), [`UNO Q`](../../arduino-uno-q) |
| Features used | Wi-Fi, HTTPS, Grove sensors, Grove Base Shield, LocalDateTime, clock, serial output, delays |
