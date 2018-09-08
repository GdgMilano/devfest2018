#!/usr/bin/env bash
yarn firestore:copy sessions backup/sessions.json
yarn firestore:copy schedule backup/schedule.json
yarn firestore:copy speakers backup/speakers.json
