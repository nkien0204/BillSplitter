/**
 * Test tích hợp: chạy FirebaseLedgerRepository / FirebaseAuthRepository thật với firestore.rules thật
 * trên Firebase Emulator Suite. Chạy bằng `npm run test:int` (tự bật emulator).
 */
/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-expo/node',
  testMatch: ['**/__tests__/**/*.int.ts'],
  moduleNameMapper: { '^@/(.*)$': '<rootDir>/src/$1' },
  testTimeout: 30000,
};
