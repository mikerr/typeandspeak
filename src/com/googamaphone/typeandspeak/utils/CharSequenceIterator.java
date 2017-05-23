
package com.googamaphone.typeandspeak.utils;

import java.text.CharacterIterator;

public final class CharSequenceIterator implements CharacterIterator, Cloneable {
    private CharSequence mCharSequence;

    /** The current position. */
    private int mCursor;

    private CharSequenceIterator(CharSequenceIterator other) {
        mCharSequence = other.mCharSequence;
        mCursor = other.mCursor;
    }

    public CharSequenceIterator(CharSequence charSequence) {
        mCharSequence = charSequence;
        mCursor = 0;
    }
    
    public void setCharSequence(CharSequence charSequence) {
        mCharSequence = charSequence;
        
        if (mCharSequence == null) {
            mCursor = 0;
        } else if (mCursor > mCharSequence.length()) {
            mCursor = mCharSequence.length();
        }
    }

    @Override
    public Object clone() {
        return new CharSequenceIterator(this);
    }

    @Override
    public int getBeginIndex() {
        return 0;
    }

    @Override
    public int getEndIndex() {
        if (mCharSequence == null) {
            return 0;
        }

        return mCharSequence.length();
    }

    @Override
    public int getIndex() {
        return mCursor;
    }

    @Override
    public char setIndex(int location) {
        if ((mCursor < getBeginIndex()) || (mCursor > getEndIndex())) {
            throw new IllegalArgumentException("Index out of bounds");
        }

        mCursor = location;

        return current();
    }

    @Override
    public char next() {
        final int nextIndex = (getIndex() + 1);
        
        if (nextIndex > getEndIndex()) {
            return CharacterIterator.DONE;
        }

        return setIndex(nextIndex);
    }

    @Override
    public char previous() {
        final int previousIndex = (getIndex() - 1);
        
        if (previousIndex < getBeginIndex()) {
            return CharacterIterator.DONE;
        }

        return setIndex(previousIndex);
    }

    @Override
    public char current() {
        final int index = getIndex();
        
        if ((index < getBeginIndex()) || (index >= getEndIndex())) {
            return CharacterIterator.DONE;
        }

        return mCharSequence.charAt(getIndex());
    }

    @Override
    public char first() {
        return setIndex(getBeginIndex());
    }

    @Override
    public char last() {
        final int lastIndex = (getEndIndex() - 1);
        
        if (lastIndex < getBeginIndex()) {
            return CharacterIterator.DONE;
        }

        return setIndex(lastIndex);
    }
}
