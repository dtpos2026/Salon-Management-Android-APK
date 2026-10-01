// Only the Firebase functions the panel uses, bundled into vendor/firebase.js.
export { initializeApp } from 'firebase/app';
export {
  getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult,
  onAuthStateChanged, signOut, connectAuthEmulator, signInWithCredential,
} from 'firebase/auth';
export {
  getFirestore, connectFirestoreEmulator, doc, getDoc, setDoc, updateDoc, deleteDoc,
  collection, query, where, orderBy, limit, startAfter, getDocs, getCountFromServer,
  runTransaction, serverTimestamp, Timestamp, writeBatch, deleteField,
} from 'firebase/firestore';
