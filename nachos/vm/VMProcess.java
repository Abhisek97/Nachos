package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.NoSuchElementException;
import java.util.Random;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		spnTable = new Integer[pageTable.length];
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
		
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
	        TranslationEntry entry = Machine.processor().readTLBEntry(i);
	        if (entry.valid) {
	        	entry.valid = false;
	            Machine.processor().writeTLBEntry(i, entry);
	            pageTable[entry.vpn] = new TranslationEntry(entry);
	        }
	    }
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
//		super.restoreState();
		
		//Is this needed??
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
	        TranslationEntry entry = Machine.processor().readTLBEntry(i);
	        if (entry.valid) {
	        	entry.valid = false;
	            Machine.processor().writeTLBEntry(i, entry);
	            pageTable[entry.vpn] = new TranslationEntry(entry);
	        }
	    }
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
//		return super.loadSections();
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);
				
				//Modification for Proj 2
				TranslationEntry entry = pageTable[vpn];
				
				UserKernel.physPageMutex.P();
				Integer thePage = null;
				try
			    {
			    	thePage = UserKernel.physicalPages.removeFirst();

			    }
			    catch (NoSuchElementException e)
			    {
			    	// Not enough physical memory left, so give the pages back
			    	// to the processor and return false
			    	unloadSections();
			    	return false;
			    }
				UserKernel.physPageMutex.V();
				
				entry.ppn = thePage;
				entry.valid = true;
				entry.readOnly = section.isReadOnly();
				section.loadPage(i, entry.ppn);
			}
		}
		
		// Modification for Proj 2: Allocating pages for text section, stack, arguments
		// i starts at numPages-9 for only modifying the pageTable entries for the 8
		// pages of stack and 1 additional page for arguments
		// TODO: May need to allocate Stack pages at the end of the pageTable
		//       from high to low memory.
		for (int i = numPages-9; i < numPages; i++) {
		    TranslationEntry entry = pageTable[i];
		    UserKernel.physPageMutex.P();
		    Integer pageNumber = null;
		    try
		    {
		    	pageNumber = UserKernel.physicalPages.removeFirst();

		    }
		    catch (NoSuchElementException e)
		    {
		    	// Not enough physical memory left, so give the pages back
		    	// to the processor and return false
		    	unloadSections();
		    	return false;
		    }
		    
		    
		    UserKernel.physPageMutex.V();
		    entry.ppn = pageNumber;
		    entry.valid = true;
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		
		case Processor.exceptionTLBMiss:
			int e = Machine.processor().readRegister(Processor.regBadVAddr);
            handleTLBMiss(e);
            break;
		case Processor.exceptionPageFault:
//			int f = Machine.processor().readRegister(Processor.regBadVAddr);
//            handleTLBMiss(f);
            break;
		default:
			super.handleException(cause);
			break;
		}
	}

	
	protected void handleTLBMiss(int miss){
        
        int tlbToBeSwapped = -1;
        
        //loops through all the entries of the tlb checking to see if its valid.
        //If not valid, designate it as the one to be swapped out
        for(int i = 0; i < Machine.processor().getTLBSize(); i++){
        	
        	//If we haven't found a tlb to be swapped
            if(tlbToBeSwapped == -1){
                
                TranslationEntry entry = Machine.processor().readTLBEntry(i);
                
                //Set the tlb to be swapped as this one cuz it's not valid
                if (!entry.valid)
                    tlbToBeSwapped = i;
            }
        }
        
        //If all tlb entries are valid, pick a random one to swap out
        if(tlbToBeSwapped == -1){
        	
        	//Need to seed?
        	Random random = new Random();
        	tlbToBeSwapped = random.nextInt(Machine.processor().getTLBSize());
        	
        	TranslationEntry swapEntry = Machine.processor().readTLBEntry(tlbToBeSwapped);
        	
        	//Check entry against page table entry for validity
        	if (swapEntry.valid) {
                this.pageTable[swapEntry.vpn].dirty = swapEntry.dirty;
                this.pageTable[swapEntry.vpn].used = swapEntry.used;
            }
        }
        
        //Get the translation entry for the missed entry
        TranslationEntry entryToBeAdded = new TranslationEntry(pageTable[miss]);
        
        while (!entryToBeAdded.valid) {
            handlePageFault(miss);
            entryToBeAdded = new TranslationEntry(pageTable[miss]);
        }
        
        //Finally add the entry in the TLB :) 
        Machine.processor().writeTLBEntry(tlbToBeSwapped, entryToBeAdded);
	}
	
	protected void handlePageFault(int fault){
		
		TranslationEntry faultEntry = pageTable[fault];
		
		faultEntry.vpn = fault;
		faultEntry.valid = true;
		
		// kernel needs a static condition variable for all pages pinned
		int ppn = -1;
		int originalHand = clockhand;
		
		do
		{
			
			try {
				ppn = VMKernel.physicalPages.removeFirst().intValue();
			} catch (Exception e) { }
			
			if (ppn < 0)
			{
				VMKernel.MetaData currMet = VMKernel.iPageTable[clockhand];
				
				if(!currMet.pinned)
				{
					// possible to be evicted if not pinned
					TranslationEntry currEntry = currMet.ownProcess.pageTable[currMet.vpn];
					if(currEntry.used)
						currEntry.used = false;
					else {
						if (currEntry.dirty) {
							// evict and swap
							
						}
						else {
							// evict without swapping
							currEntry.valid = false;
							// 
							
							//
						}
					}
				}
				
				clockhand = (clockhand + 1) % VMKernel.iPageTable.length;
				if (clockhand == originalHand)
				{
					// condition variable wait
				}
			}
			
		} while (ppn < 0);
		
		//fault ppn?
		
		//VMKernel.invertedPageTable[ppn] = this;
		
		for(int i=0; i<coff.getNumSections(); i++) {
			
			CoffSection sect = coff.getSection(i);
			
			
		}
	}
	
	private static int clockhand = 0;
	
	private Integer[] spnTable;
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
