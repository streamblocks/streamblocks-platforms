/**
 * CmdArgs - A very simple command line arguments src file, can be included
 * in C++ and C files using the appropriate function calls.  In order to 
 * use this to process your arguments simply do the following:
 * //create a CmdArgs object
 * CmdArgs *args = new CmdArgs(std::string(argv[0])); //sets program name for object
 * //define static variables for options
 * static bool myFirstOption = false; //assigning var gives default value
 * //add options to CmdArgs
 * cmd->addOption(new Option<bool>(&myFirstOption,"-mFO","An option"));
 * //general form of
 * cmd->addOption(new Option<type>(var mem location, text flag, text description));
 * //give cmd line args to CmdArgs
 * cmd->processArgs(argc,argv)
 * .....now you can use the variables as you see fit
 *
 * @author Jonathan Beard
 * @version Last modified 27 May 2012
 * @contact jbeard@wustl.edu
 */ 


#include <iostream>
#include <algorithm>
#include <queue>
#include <cassert>
#include <cstring>
#include <cstdlib>
#include <cassert>
#include "command_arguments.h"



CmdArgs::CmdArgs(const std::string n, 
                 std::ostream &user,
                 std::ostream &err )
   : name( n ),
     userstream( user ),
     errorstream( err )
{
  /* nothing to do here, move along */
}

CmdArgs::~CmdArgs()
{
    for( auto *ptr : options )
    {
        delete( ptr );
        ptr = nullptr;
    }
}

void CmdArgs::printArgs(){
   char stars[81];
   generateStars( stars, 81 );
   userstream << stars << std::endl;
   userstream << "Options menu for: " << name << std::endl;
   for_each( options.begin(),
             options.end(),
             [&]( OptionBase *option ){ 
                  userstream << 
                        option->toString() << std::endl; } );
   userstream << "End Options" << std::endl;
   userstream << stars << std::endl;
}

void CmdArgs::printSettings()
{
   char stars[ 81 ];
   generateStars( stars, 81 );
   userstream << stars << std::endl;
   userstream << "Current Settings:\n";
   for_each( options.begin(),
             options.end(),
             [&]( OptionBase *option )
             {
               userstream << option->get_flag() << ":    " << option->getValue() << "\n";
             } );
   userstream << stars << std::endl;
}

void 
CmdArgs::addOption(OptionBase *option){
   assert( option != nullptr );
   options.push_back(option);
}

void CmdArgs::processArgs(int argc, char **argv){
   /* store for later just in case */
   this->argc = argc;
   this->argv = argv;

   /* now on to the processing */
   std::queue< std::string > ignored_options;
   for(int i = 1 ; i < argc; i++)
   {
      for( auto it( options.begin() ); it != options.end(); ++it )
      {
       if( std::strcmp( /* given argument */ argv[i],
                        /* given flag     */ (*it)->get_flag().c_str() ) == 0 )
         {
            bool success( false );
            if( i + 1 < argc )
            {
               success = (*it)->setValue( argv[ ++i ] );
            }
            if( success != true )
            {
               if( (*it)->is_bool() )
               {
                  (*it)->setValue( "true" );
                  /** rewind i **/
                  if( i != argc - 1 )
                  {
                     --i;
                  }
               }
               else
               {
                  errorstream << "Invalid input for flag (" <<
                   (*it)->get_flag() << ") : " << argv[i] << "\n";
               }
            }
            goto END;
         }
    }
      ignored_options.push( std::string( argv[i] ) );
      END:;
   }
   
   if(! ignored_options.empty() ){
      errorstream << 
         "The following options were unknown and ignored: \n";
   }
   while(! ignored_options.empty() ){
      std::string option = ignored_options.front();
      ignored_options.pop();
      errorstream << option << std::endl;
   }
}

char**   
CmdArgs::getOriginalArguments()
{
   return( argv );
}

int
CmdArgs::getOriginalArgumentCount()
{
   return( argc );
}

bool
CmdArgs::allMandatorySet()
{
   bool ret_val( true );
   for( OptionBase * const option : options )
   {
      assert( option != nullptr );
      if( option->is_mandatory() && ! option->is_set() )
      {
         if( ret_val /** not set yet **/ )
         {
           errorstream << "\033[1;36m" << "The following options must be set:" << "\033[0m" << "\n";
         }
         errorstream << "\033[1;31m" << "Option: " << "\033[0m" << option->toString() << "\n"; 
         /** return false for rest, continue to print all not set **/
         ret_val = false;
      }
   }
   return( ret_val );
}

void 
CmdArgs::generateStars( char *buffer, const std::size_t bsize )
{
   assert( buffer != nullptr );
   std::memset( buffer, '*', sizeof(char) * bsize - 2 );
   buffer[ bsize - 1 ] = '\0';
   return;
}
